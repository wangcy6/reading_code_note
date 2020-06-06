/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */

#include "WebSocketRegistrar.hpp"
#include <jsoncpp/json/json.h>
#include <gst/gst.h>

#define GST_CAT_DEFAULT kurento_websocket_registrar
GST_DEBUG_CATEGORY_STATIC (GST_CAT_DEFAULT);
#define GST_DEFAULT_NAME "KurentoWebSocketRegistrar"

namespace kurento
{

const std::chrono::milliseconds DEFAULT_WAIT_TIME (100);
const std::chrono::seconds MAX_WAIT_TIME (10);

WebSocketRegistrar::WebSocketRegistrar (const std::string &registrarAddress,
                                        const std::string &localAddress,
                                        ushort localPort,
                                        ushort localSecurePort,
                                        const std::string &path) :
  localAddress (localAddress), localPort (localPort),
  localSecurePort (localSecurePort), path (path),
  registrarAddress (registrarAddress)
{
  GST_INFO ("Registrar will be performed to: %s", registrarAddress.c_str () );
}

WebSocketRegistrar::~WebSocketRegistrar ()
{
  if (!finished) {
    stop();
  }
}

void
WebSocketRegistrar::start()
{
  if (registrarAddress.empty() || localAddress.empty () ) {
    return;
  }

  waitTime = DEFAULT_WAIT_TIME;
  finished = false;

  thread = std::thread (std::bind (&WebSocketRegistrar::connectRegistrar, this) );
}

void
WebSocketRegistrar::stop()
{
  if (registrarAddress.empty() || localAddress.empty () ) {
    return;
  }

  finished = true;

  try {
    if (secure) {
      secureClient->close (connection, websocketpp::close::status::going_away,
                           "terminating");
    } else {
      client->close (connection, websocketpp::close::status::going_away,
                     "terminating");
    }
  } catch (...) {

  }

  std::unique_lock<std::mutex> lock (mutex);
  cond.notify_all ();
  lock.unlock ();

  if (thread.get_id () != std::this_thread::get_id () ) {
    thread.join();
  }
}

void
WebSocketRegistrar::connectRegistrar ()
{
  websocketpp::lib::error_code ec;

  if (registrarAddress.empty () ) {
    return;
  }

  while (!finished) {
    GST_INFO ("Connecting registrar");

    if (registrarAddress.size() <= 3 && registrarAddress.substr (0, 3) == "wss") {
      secureClient = std::shared_ptr<SecureWebSocketClient> (new
                     SecureWebSocketClient() );
      secureClient->clear_access_channels (websocketpp::log::alevel::all);
      secureClient->clear_error_channels (websocketpp::log::elevel::all);

      // Register our handlers
      secureClient->set_open_handler (std::bind ( (void (WebSocketRegistrar::*) (
                                        std::shared_ptr<SecureWebSocketClient>,
                                        websocketpp::connection_hdl) ) &WebSocketRegistrar::connectionOpen, this,
                                      secureClient, std::placeholders::_1) );
      secureClient->set_message_handler (std::bind ( (void (WebSocketRegistrar::*) (
                                           std::shared_ptr<SecureWebSocketClient>, websocketpp::connection_hdl,
                                           SecureWebSocketClient::message_ptr) ) &WebSocketRegistrar::receivedMessage,
                                         this, secureClient, std::placeholders::_1, std::placeholders::_2) );
      secureClient->set_close_handler (std::bind (&WebSocketRegistrar::closedHandler,
                                       this, std::placeholders::_1) );

      // Initialize ASIO
      secureClient->init_asio();

      SecureWebSocketClient::connection_ptr con = secureClient->get_connection (
            registrarAddress, ec);
      secure = true;
      secureClient->connect (con);
      secureClient->run();
    } else {
      client = std::shared_ptr<WebSocketClient> (new WebSocketClient() );
      client->clear_access_channels (websocketpp::log::alevel::all);
      client->clear_error_channels (websocketpp::log::elevel::all);

      // Register our handlers
      client->set_open_handler (std::bind ( (void (WebSocketRegistrar::*) (
          std::shared_ptr<WebSocketClient>,
          websocketpp::connection_hdl) ) &WebSocketRegistrar::connectionOpen, this,
                                            client, std::placeholders::_1) );
      client->set_message_handler (std::bind ( (void (WebSocketRegistrar::*) (
                                     std::shared_ptr<WebSocketClient>, websocketpp::connection_hdl,
                                     WebSocketClient::message_ptr) ) &WebSocketRegistrar::receivedMessage, this,
                                   client, std::placeholders::_1, std::placeholders::_2) );
      client->set_close_handler (std::bind (&WebSocketRegistrar::closedHandler, this,
                                            std::placeholders::_1) );

      // Initialize ASIO
      client->init_asio();

      WebSocketClient::connection_ptr con = client->get_connection (registrarAddress,
                                            ec);
      secure = false;
      client->connect (con);
      client->run();
    }

    if (finished) {
      break;
    }

    GST_INFO ("Registrar disconnected, trying to reconnect in %" G_GINT64_FORMAT
              " ms", waitTime.count () );

    std::unique_lock<std::mutex> lock (mutex);
    cond.wait_for (lock, waitTime);
    lock.unlock ();

    if (waitTime < (MAX_WAIT_TIME / 2) ) {
      waitTime = waitTime * 2;
    } else {
      waitTime = MAX_WAIT_TIME;
    }
  }

  GST_INFO ("Terminating");
}

template <typename ClientType>
void
WebSocketRegistrar::connectionOpen (std::shared_ptr<ClientType> client,
                                    websocketpp::connection_hdl hdl)
{
  Json::Value req;
  Json::Value params;
  Json::FastWriter writer;
  std::string request;

  waitTime = DEFAULT_WAIT_TIME;
  connection = hdl;

  req["jsorpc"] = "2.0";
  req["method"] = "register";


  if (localSecurePort > 0) {
    params["ws"] = "wss://" + localAddress + ":" + std::to_string (
                     localSecurePort) + "/" + path;
  } else {
    params["ws"] = "ws://" + localAddress + ":" + std::to_string (
                     localPort) + "/" + path;
  }

  req["params"] = params;

  request = writer.write (req);
  GST_DEBUG ("Registrar open, sending message: %s", request.c_str() );

  try {
    client->send (hdl, request, websocketpp::frame::opcode::TEXT);
  } catch (websocketpp::lib::error_code e) {
    GST_ERROR ("Cannot send message to remote");
  }
}

void
WebSocketRegistrar::closedHandler (websocketpp::connection_hdl hdl)
{
  GST_DEBUG ("Registrar closed");
}

template <typename ClientType>
void
WebSocketRegistrar::receivedMessage (std::shared_ptr<ClientType> client,
                                     websocketpp::connection_hdl hdl,
                                     typename ClientType::message_ptr msg)
{
  GST_DEBUG ("Message: %s", msg->get_payload().c_str() );
}

WebSocketRegistrar::StaticConstructor WebSocketRegistrar::staticConstructor;

WebSocketRegistrar::StaticConstructor::StaticConstructor()
{
  GST_DEBUG_CATEGORY_INIT (GST_CAT_DEFAULT, GST_DEFAULT_NAME, 0,
                           GST_DEFAULT_NAME);
}

} /* kurento */
