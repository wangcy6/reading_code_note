/*
 * (C) Copyright 2016 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "kmsrtpsynchronizer.h"
#include <glib/gstdio.h>

#include <sys/stat.h>  // 'ACCESSPERMS' is not POSIX, requires GNU extensions in GCC

#define GST_DEFAULT_NAME "rtpsynchronizer"
GST_DEBUG_CATEGORY_STATIC (kms_rtp_synchronizer_debug_category);
#define GST_CAT_DEFAULT kms_rtp_synchronizer_debug_category

#define parent_class kms_rtp_synchronizer_parent_class
G_DEFINE_TYPE (KmsRtpSynchronizer, kms_rtp_synchronizer, G_TYPE_OBJECT);

#define KMS_RTP_SYNCHRONIZER_LOCK(rtpsynchronizer) \
  (g_rec_mutex_lock (&KMS_RTP_SYNCHRONIZER_CAST ((rtpsynchronizer))->priv->mutex))
#define KMS_RTP_SYNCHRONIZER_UNLOCK(rtpsynchronizer) \
  (g_rec_mutex_unlock (&KMS_RTP_SYNCHRONIZER_CAST ((rtpsynchronizer))->priv->mutex))

#define KMS_RTP_SYNCHRONIZER_GET_PRIVATE(obj) ( \
  G_TYPE_INSTANCE_GET_PRIVATE (                 \
    (obj),                                      \
    KMS_TYPE_RTP_SYNCHRONIZER,                  \
    KmsRtpSynchronizerPrivate                   \
  )                                             \
)

#define KMS_RTP_SYNC_STATS_PATH_ENV_VAR "KMS_RTP_SYNC_STATS_PATH"
static const gchar *stats_files_dir = NULL;

struct _KmsRtpSynchronizerPrivate
{
  GRecMutex mutex;

  gboolean feeded_sorted;

  guint32 ssrc;
  gint32 pt;
  gint32 clock_rate;

  gboolean base_initiated;
  GstClockTime base_ntp_time;
  GstClockTime base_sync_time;

  guint64 rtp_ext_ts; // Extended timestamp: robust against input wraparound
  guint64 last_sr_rtp_ext_ts;
  GstClockTime last_sr_ntp_time;

  /* Interpolate PTSs */
  gboolean base_interpolate_initiated;
  GstClockTime base_interpolate_ext_ts;
  GstClockTime base_interpolate_time;

  /* Feeded sorted case */
  guint64 fs_last_rtp_ext_ts;
  GstClockTime fs_last_pts_time;

  /* Stats recording */
  FILE *stats_file;
  GMutex stats_mutex;
};

static void
kms_rtp_synchronizer_finalize (GObject * object)
{
  KmsRtpSynchronizer *self = KMS_RTP_SYNCHRONIZER (object);

  GST_DEBUG_OBJECT (self, "finalize");

  if (self->priv->stats_file) {
    fclose (self->priv->stats_file);
  }
  g_mutex_clear (&self->priv->stats_mutex);

  g_rec_mutex_clear (&self->priv->mutex);

  G_OBJECT_CLASS (parent_class)->finalize (object);
}

static void
kms_rtp_synchronizer_class_init (KmsRtpSynchronizerClass * klass)
{
  GObjectClass *gobject_class;

  gobject_class = G_OBJECT_CLASS (klass);
  gobject_class->finalize = kms_rtp_synchronizer_finalize;

  GST_DEBUG_CATEGORY_INIT (GST_CAT_DEFAULT, GST_DEFAULT_NAME, 0,
      GST_DEFAULT_NAME);

  g_type_class_add_private (klass, sizeof (KmsRtpSynchronizerPrivate));

  stats_files_dir = g_getenv (KMS_RTP_SYNC_STATS_PATH_ENV_VAR);
}

static void
kms_rtp_synchronizer_init (KmsRtpSynchronizer * self)
{
  self->priv = KMS_RTP_SYNCHRONIZER_GET_PRIVATE (self);

  g_rec_mutex_init (&self->priv->mutex);
  g_mutex_init (&self->priv->stats_mutex);

  // 'gst_rtp_buffer_ext_timestamp()' requires an initial value of -1
  self->priv->rtp_ext_ts = (guint64)-1;  // == G_MAXUINT64
  self->priv->fs_last_rtp_ext_ts = (guint64)-1;

  self->priv->fs_last_pts_time = GST_CLOCK_TIME_NONE;
}

static void
kms_rtp_synchronizer_init_stats_file (KmsRtpSynchronizer * self,
    const gchar * stats_name)
{
  gchar *stats_file_name;
  GDateTime *datetime;
  gchar *date_str;

  if (stats_name == NULL) {
    GST_INFO_OBJECT (self, "No name for stats file");
    return;
  }

  if (stats_files_dir == NULL) {
    GST_INFO_OBJECT (self, "No path for stats; enable with env variable: '%s'",
        KMS_RTP_SYNC_STATS_PATH_ENV_VAR);
    return;
  }

  datetime = g_date_time_new_now_local ();
  date_str = g_date_time_format (datetime, "%Y%m%d%H%M%S");
  g_date_time_unref (datetime);

  stats_file_name =
      g_strdup_printf ("%s/%s_%s.csv", stats_files_dir, date_str, stats_name);
  g_free (date_str);

  if (g_mkdir_with_parents (stats_files_dir, ACCESSPERMS) < 0) {
    GST_ERROR_OBJECT (self,
        "Cannot create directory for stats: %s", stats_files_dir);
    goto end;
  }

  self->priv->stats_file = g_fopen (stats_file_name, "w+");

  if (self->priv->stats_file == NULL) {
    GST_ERROR_OBJECT (self, "Cannot open file for stats: %s", stats_file_name);
  } else {
    GST_INFO_OBJECT (self, "File for stats: %s", stats_file_name);
    g_fprintf (self->priv->stats_file,
        "ENTRY_TS,THREAD,SSRC,CLOCK_RATE,PTS_ORIG,PTS,DTS,EXT_RTP,SR_NTP_NS,SR_EXT_RTP\n");
  }

end:
  g_free (stats_file_name);
}

KmsRtpSynchronizer *
kms_rtp_synchronizer_new (gboolean feeded_sorted, const gchar * stats_name)
{
  KmsRtpSynchronizer *self;

  self = KMS_RTP_SYNCHRONIZER (g_object_new (KMS_TYPE_RTP_SYNCHRONIZER, NULL));

  self->priv->feeded_sorted = feeded_sorted;
  kms_rtp_synchronizer_init_stats_file (self, stats_name);

  return self;
}

gboolean
kms_rtp_synchronizer_add_clock_rate_for_pt (KmsRtpSynchronizer * self,
    gint32 pt, gint32 clock_rate, GError ** error)
{
  gboolean ret = FALSE;

  if (clock_rate <= 0) {
    const gchar *msg = "clock-rate <= 0 no allowed.";

    GST_ERROR_OBJECT (self, "%s", msg);
    g_set_error_literal (error, KMS_RTP_SYNC_ERROR, KMS_RTP_SYNC_INVALID_DATA,
        msg);

    return FALSE;
  }

  KMS_RTP_SYNCHRONIZER_LOCK (self);

  /* TODO: allow more than one PT */
  if (self->priv->clock_rate != 0) {
    const gchar *msg = "Only one PT allowed.";

    GST_ERROR_OBJECT (self, "%s", msg);
    g_set_error_literal (error, KMS_RTP_SYNC_ERROR, KMS_RTP_SYNC_INVALID_DATA,
        msg);

    goto end;
  }

  self->priv->pt = pt;
  self->priv->clock_rate = clock_rate;

  ret = TRUE;

end:
  KMS_RTP_SYNCHRONIZER_UNLOCK (self);

  return ret;
}

static void
kms_rtp_synchronizer_process_rtcp_packet (KmsRtpSynchronizer * self,
    GstRTCPPacket * packet, GstClockTime current_time)
{
  guint32 ssrc, rtp_ts;
  guint64 ntp_ts;
  GstClockTime ntp_time;

  const GstRTCPType type = gst_rtcp_packet_get_type (packet);
  if (type != GST_RTCP_TYPE_SR) {
    GST_DEBUG_OBJECT (self, "Ignore RTCP packet, type: %d", type);
    return;
  }

  gst_rtcp_packet_sr_get_sender_info (packet, &ssrc, &ntp_ts, &rtp_ts,
      NULL, NULL);

  // NTP field of SR is in NTP Timestamp Format: 32.32 unsigned fixed point
  // RTP field of SR is a timestamp in clock rate units
  // See RFC 3550: https://tools.ietf.org/html/rfc3550
  // Convert the NTP timestamp from 32.32 to time in nanoseconds
  ntp_time = gst_util_uint64_scale (ntp_ts, GST_SECOND, (1LL << 32));

  GST_DEBUG_OBJECT (self, "Process RTCP Sender Report"
      ", SSRC: %u, RTP ts: %u"
      ", NTP time: %" GST_TIME_FORMAT ", current time: %" GST_TIME_FORMAT,
      ssrc, rtp_ts, GST_TIME_ARGS (ntp_time), GST_TIME_ARGS (current_time));

  KMS_RTP_SYNCHRONIZER_LOCK (self);

  if (!self->priv->base_initiated) {
    GST_DEBUG_OBJECT (self, "RTCP SR received: stop interpolating PTS");
    self->priv->base_ntp_time = ntp_time;
    self->priv->base_sync_time = current_time;
    self->priv->base_initiated = TRUE;
  }

  self->priv->last_sr_rtp_ext_ts =
      gst_rtp_buffer_ext_timestamp (&self->priv->rtp_ext_ts, rtp_ts);

  self->priv->last_sr_ntp_time = ntp_time;

  KMS_RTP_SYNCHRONIZER_UNLOCK (self);
}

gboolean
kms_rtp_synchronizer_process_rtcp_buffer (KmsRtpSynchronizer * self,
    GstBuffer * buffer, GError ** error)
{
  GstRTCPBuffer rtcp_buffer = GST_RTCP_BUFFER_INIT;
  GstRTCPPacket packet;

  if (!gst_rtcp_buffer_map (buffer, GST_MAP_READ, &rtcp_buffer)) {
    const gchar *msg = "Buffer cannot be mapped as RTCP";

    GST_ERROR_OBJECT (self, "%s", msg);
    g_set_error_literal (error, KMS_RTP_SYNC_ERROR,
        KMS_RTP_SYNC_UNEXPECTED_ERROR, msg);

    return FALSE;
  }

  if (!gst_rtcp_buffer_get_first_packet (&rtcp_buffer, &packet)) {
    GST_WARNING_OBJECT (self, "Empty RTCP buffer");
    goto unmap;
  }

  kms_rtp_synchronizer_process_rtcp_packet (self, &packet,
      GST_BUFFER_DTS (buffer));

unmap:
  gst_rtcp_buffer_unmap (&rtcp_buffer);

  return TRUE;
}

static void
kms_rtp_synchronizer_rtp_diff_full (KmsRtpSynchronizer * self,
    GstRTPBuffer * rtp_buffer, gint32 clock_rate, guint64 base_ext_ts,
    gboolean wrapped_down, gboolean wrapped_up)
{
  GstBuffer *buffer = rtp_buffer->buffer;
  guint64 diff_rtp_ext_ts, diff_rtp_time;

  if (self->priv->rtp_ext_ts > base_ext_ts) {
    diff_rtp_ext_ts = self->priv->rtp_ext_ts - base_ext_ts;
    diff_rtp_time =
        gst_util_uint64_scale_int (diff_rtp_ext_ts, GST_SECOND, clock_rate);

    if (wrapped_up) {
      GST_WARNING_OBJECT (self, "PTS wrapped up, setting MAXUINT64");
      GST_BUFFER_PTS (buffer) = G_MAXUINT64;
    } else if (wrapped_down
        && (diff_rtp_time < (G_MAXUINT64 - GST_BUFFER_PTS (buffer)))) {
      GST_WARNING_OBJECT (self, "PTS wrapped down, setting to 0");
      GST_BUFFER_PTS (buffer) = 0;
    } else if (!wrapped_down
        && (diff_rtp_time > (G_MAXUINT64 - GST_BUFFER_PTS (buffer)))) {
      GST_WARNING_OBJECT (self,
          "Diff RTP time > (MAXUINT64 - base PTS), setting MAXUINT64");
      GST_BUFFER_PTS (buffer) = G_MAXUINT64;
    } else {
      GST_BUFFER_PTS (buffer) += diff_rtp_time;
    }
  }
  else if (self->priv->rtp_ext_ts < base_ext_ts) {
    diff_rtp_ext_ts = base_ext_ts - self->priv->rtp_ext_ts;
    diff_rtp_time =
        gst_util_uint64_scale_int (diff_rtp_ext_ts, GST_SECOND, clock_rate);

    if (wrapped_down) {
      GST_WARNING_OBJECT (self, "PTS wrapped down, setting to 0");
      GST_BUFFER_PTS (buffer) = 0;
    } else if (wrapped_up && (diff_rtp_time < GST_BUFFER_PTS (buffer))) {
      GST_WARNING_OBJECT (self, "PTS wrapped up, setting to MAXUINT64");
      GST_BUFFER_PTS (buffer) = G_MAXUINT64;
    } else if (!wrapped_up && (diff_rtp_time > GST_BUFFER_PTS (buffer))) {
      GST_WARNING_OBJECT (self,
          "Diff RTP ns time greater than base PTS, setting to 0");
      GST_BUFFER_PTS (buffer) = 0;
    } else {
      GST_BUFFER_PTS (buffer) -= diff_rtp_time;
    }
  }
  else {                      /* if equals */
    if (wrapped_down) {
      GST_WARNING_OBJECT (self, "PTS wrapped down, setting to 0");
      GST_BUFFER_PTS (buffer) = 0;
    } else if (wrapped_up) {
      GST_WARNING_OBJECT (self, "PTS wrapped up, setting MAXUINT64");
      GST_BUFFER_PTS (buffer) = G_MAXUINT64;
    }
  }
}

static void
kms_rtp_synchronizer_rtp_diff (KmsRtpSynchronizer * self,
    GstRTPBuffer * rtp_buffer, gint32 clock_rate, guint64 base_ext_ts)
{
  kms_rtp_synchronizer_rtp_diff_full (self, rtp_buffer, clock_rate, base_ext_ts,
      FALSE, FALSE);
}

static void
kms_rtp_synchronizer_write_stats (KmsRtpSynchronizer * self, guint32 ssrc,
    guint32 clock_rate, guint64 pts_orig, guint64 pts, guint64 dts,
    guint64 rtp_ext_ts, guint64 last_sr_ntp_ns_time, guint64 last_sr_rtp_ext_ts)
{
  if (self->priv->stats_file == NULL) {
    return;
  }

  g_mutex_lock (&self->priv->stats_mutex);
  g_fprintf (self->priv->stats_file,
      "%" G_GUINT64_FORMAT ",%p,%" G_GUINT32_FORMAT ",%" G_GUINT32_FORMAT ",%"
      G_GUINT64_FORMAT ",%" G_GUINT64_FORMAT ",%" G_GUINT64_FORMAT ",%"
      G_GUINT64_FORMAT ",%" G_GUINT64_FORMAT ",%" G_GUINT64_FORMAT "\n",
      g_get_real_time (), g_thread_self (), ssrc, clock_rate, pts_orig,
      pts, dts, rtp_ext_ts, last_sr_ntp_ns_time, last_sr_rtp_ext_ts);
  g_mutex_unlock (&self->priv->stats_mutex);
}

static gboolean
kms_rtp_synchronizer_process_rtp_buffer_mapped (KmsRtpSynchronizer * self,
    GstRTPBuffer * rtp_buffer, GError ** error)
{
  GstBuffer *buffer = rtp_buffer->buffer;
  guint64 pts_orig, rtp_ext_ts, last_sr_rtp_ext_ts, last_sr_ntp_time_ns;
  guint64 diff_ntp_time_ns;
  guint32 ssrc, rtp_ts;
  gint32 clock_rate;
  guint16 rtp_seq;
  guint8 pt;
  gboolean ret = TRUE;

  KMS_RTP_SYNCHRONIZER_LOCK (self);

  ssrc = gst_rtp_buffer_get_ssrc (rtp_buffer);
  rtp_seq = gst_rtp_buffer_get_seq (rtp_buffer);

  GST_LOG_OBJECT (self, "RTP SSRC: %u, Seq: %u", ssrc, rtp_seq);

  if (self->priv->ssrc == 0) {
    self->priv->ssrc = ssrc;
  }
  else if (ssrc != self->priv->ssrc) {
    gchar *msg = g_strdup_printf ("Invalid SSRC (%u), not matching with %u",
        ssrc, self->priv->ssrc);

    GST_ERROR_OBJECT (self, "%s", msg);
    g_set_error_literal (error, KMS_RTP_SYNC_ERROR, KMS_RTP_SYNC_INVALID_DATA,
        msg);
    g_free (msg);

    KMS_RTP_SYNCHRONIZER_UNLOCK (self);

    return FALSE;
  }

  pt = gst_rtp_buffer_get_payload_type (rtp_buffer);
  if (pt != self->priv->pt || self->priv->clock_rate <= 0) {
    gchar *msg;
    if (pt != self->priv->pt) {
      msg =
          g_strdup_printf ("Unknown PT: %u, expected: %u", pt, self->priv->pt);
    } else {
      msg =
          g_strdup_printf ("Invalid clock-rate: %d", self->priv->clock_rate);
    }

    GST_ERROR_OBJECT (self, "%s", msg);
    g_set_error_literal (error, KMS_RTP_SYNC_ERROR, KMS_RTP_SYNC_INVALID_DATA,
        msg);
    g_free (msg);

    KMS_RTP_SYNCHRONIZER_UNLOCK (self);

    return FALSE;
  }

  pts_orig = GST_BUFFER_PTS (buffer);
  rtp_ts = gst_rtp_buffer_get_timestamp (rtp_buffer);
  gst_rtp_buffer_ext_timestamp (&self->priv->rtp_ext_ts, rtp_ts);

  if (self->priv->feeded_sorted) {
    if (self->priv->fs_last_rtp_ext_ts != (guint64) -1
        && self->priv->rtp_ext_ts < self->priv->fs_last_rtp_ext_ts) {
      guint16 seq = gst_rtp_buffer_get_seq (rtp_buffer);
      gchar *msg =
          g_strdup_printf
          ("Received an unsorted RTP buffer when expecting sorted (ssrc: %"
          G_GUINT32_FORMAT ", seq: %" G_GUINT16_FORMAT ", ts: %"
          G_GUINT32_FORMAT ", ext_ts: %" G_GUINT64_FORMAT
          "). Moving to unsorted mode",
          ssrc, seq, rtp_ts, self->priv->rtp_ext_ts);

      GST_WARNING_OBJECT (self, "%s", msg);
      g_set_error_literal (error, KMS_RTP_SYNC_ERROR, KMS_RTP_SYNC_INVALID_DATA,
          msg);
      g_free (msg);

      self->priv->feeded_sorted = FALSE;
      ret = FALSE;
    }
    else if (self->priv->rtp_ext_ts == self->priv->fs_last_rtp_ext_ts) {
      if (GST_CLOCK_TIME_IS_VALID (self->priv->fs_last_pts_time)) {
        GST_BUFFER_PTS (buffer) = self->priv->fs_last_pts_time;
      }
      goto end;
    }
  }

  if (!self->priv->base_initiated) {
    GST_DEBUG_OBJECT (self, "RTCP SR not received yet: interpolate PTS"
        ", SSRC: %u, PT: %u", ssrc, pt);

    if (!self->priv->base_interpolate_initiated) {
      self->priv->base_interpolate_ext_ts = self->priv->rtp_ext_ts;
      self->priv->base_interpolate_time = GST_BUFFER_PTS (buffer);
      self->priv->base_interpolate_initiated = TRUE;
    }
    else {
      GST_BUFFER_PTS (buffer) = self->priv->base_interpolate_time;
      kms_rtp_synchronizer_rtp_diff (self, rtp_buffer, self->priv->clock_rate,
          self->priv->base_interpolate_ext_ts);
    }
  }
  else {
    gboolean wrapped_down, wrapped_up;

    wrapped_down = wrapped_up = FALSE;

    GST_BUFFER_PTS (buffer) = self->priv->base_sync_time;

    if (self->priv->last_sr_ntp_time > self->priv->base_ntp_time) {
      diff_ntp_time_ns =
          self->priv->last_sr_ntp_time - self->priv->base_ntp_time;
      wrapped_up = (diff_ntp_time_ns > (G_MAXUINT64 - GST_BUFFER_PTS (buffer)));
      GST_BUFFER_PTS (buffer) += diff_ntp_time_ns;
    }
    else if (self->priv->last_sr_ntp_time < self->priv->base_ntp_time) {
      diff_ntp_time_ns =
          self->priv->base_ntp_time - self->priv->last_sr_ntp_time;
      wrapped_down = (GST_BUFFER_PTS (buffer) < diff_ntp_time_ns);
      GST_BUFFER_PTS (buffer) -= diff_ntp_time_ns;
    }
    /* if equals do nothing */

    kms_rtp_synchronizer_rtp_diff_full (self, rtp_buffer,
        self->priv->clock_rate, self->priv->last_sr_rtp_ext_ts, wrapped_down,
        wrapped_up);
  }

  if (self->priv->feeded_sorted) {
    const GstClockTime pts_current = GST_BUFFER_PTS (buffer);
    GstClockTime pts_fixed = pts_current;

    if (GST_CLOCK_TIME_IS_VALID (self->priv->fs_last_pts_time)
        && pts_current < self->priv->fs_last_pts_time) {

      guint16 seq = gst_rtp_buffer_get_seq (rtp_buffer);
      pts_fixed = self->priv->fs_last_pts_time;

      GST_WARNING_OBJECT (self,
          "[Sorted mode] Fix PTS not increasing monotonically"
          ", SSRC: %" G_GUINT32_FORMAT ", seq: %" G_GUINT16_FORMAT
          ", rtp_ts: %" G_GUINT32_FORMAT ", ext_ts: %" G_GUINT64_FORMAT
          ", last: %" GST_TIME_FORMAT
          ", current: %" GST_TIME_FORMAT
          ", fixed = last: %" GST_TIME_FORMAT,
          ssrc, seq, rtp_ts, self->priv->rtp_ext_ts,
          GST_TIME_ARGS (self->priv->fs_last_pts_time),
          GST_TIME_ARGS (pts_current),
          GST_TIME_ARGS (pts_fixed));

      GST_BUFFER_PTS (buffer) = pts_fixed;
    }

    self->priv->fs_last_rtp_ext_ts = self->priv->rtp_ext_ts;
    self->priv->fs_last_pts_time = pts_fixed;
  }

end:
  clock_rate = self->priv->clock_rate;
  rtp_ext_ts = self->priv->rtp_ext_ts;
  last_sr_rtp_ext_ts = self->priv->last_sr_rtp_ext_ts;
  last_sr_ntp_time_ns = self->priv->last_sr_ntp_time;

  KMS_RTP_SYNCHRONIZER_UNLOCK (self);

  kms_rtp_synchronizer_write_stats (self, ssrc, clock_rate,
      pts_orig, GST_BUFFER_PTS (buffer), GST_BUFFER_DTS (buffer), rtp_ext_ts,
      last_sr_ntp_time_ns, last_sr_rtp_ext_ts);

  return ret;
}

gboolean
kms_rtp_synchronizer_process_rtp_buffer (KmsRtpSynchronizer * self,
    GstBuffer * buffer, GError ** error)
{
  GstRTPBuffer rtp_buffer = GST_RTP_BUFFER_INIT;
  gboolean ret;

  if (!gst_rtp_buffer_map (buffer, GST_MAP_READ, &rtp_buffer)) {
    const gchar *msg = "Buffer cannot be mapped as RTP";

    GST_ERROR_OBJECT (self, "%s", msg);
    g_set_error_literal (error, KMS_RTP_SYNC_ERROR,
        KMS_RTP_SYNC_UNEXPECTED_ERROR, msg);
    return FALSE;
  }

  ret =
      kms_rtp_synchronizer_process_rtp_buffer_mapped (self, &rtp_buffer, error);

  gst_rtp_buffer_unmap (&rtp_buffer);

  return ret;
}
