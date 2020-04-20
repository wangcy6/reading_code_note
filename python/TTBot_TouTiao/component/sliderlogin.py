#coding:utf-8

import time
import cv2
import copy

from settings import *
from deco.login import inited
from selenium import webdriver
from component.log import getLogger
from util.tools import format_cookies
from util.slider import download_img,drag_and_drop,save_cookie
from selenium.webdriver.chrome.options import Options
from bs4 import BeautifulSoup as bs 
from config import CHROME_PATH,USERNAME,PASSWORD,\
    IMG_BIG_PATH,IMG_BLOCK_PATH,COOKIE_FILE,SLIDER_DEBUG

logger = getLogger(__name__)

class SliderHelper:

    def __init__(self):
        self.username = None
        self.password = None
        self.driver = None
        self._cookies = None
        self.headers = copy.deepcopy(HEADERS)

    def init_chrome(self):
        chrome_options = Options()
        chrome_options.add_argument('--ignore-certificate-errors')
        chrome_options.add_argument('--disable-gpu')
        chrome_options = webdriver.ChromeOptions()
        self.driver = webdriver.Chrome(executable_path=CHROME_PATH, options=chrome_options)

    @inited 
    def login(self,username=USERNAME,password=PASSWORD):
        self.username = username
        self.password = password
        self.driver.get(URL_LOGIN_HOME)
        self.__account_input()
        self._slider_img_download()
        self._validate_and_drag(DOWN=LOGIN_VERIFY)
        time.sleep(2)
        cookies = self.driver.get_cookies()
        self.driver.get(URL_WENDA)
        time.sleep(2)
        self._cookies = self.driver.get_cookies()
        c = self._cookies + cookies
        self.headers['cookie'] = format_cookies(c)
        logger.info(f'成功滑动登陆!cookie:{self.headers["cookie"]}')
        self.stop()
        save_cookie(COOKIE_FILE,self.headers['cookie'])
        return self.headers

    def __account_input(self):
        login_icon = self.driver.find_element_by_class_name(LOGIN_ICON_CLASS)
        login_icon.click()
        username_input = self.driver.find_element_by_id(USERNAME_ID)
        password_input = self.driver.find_element_by_id(PASSWORD_ID)
        login_button = self.driver.find_element_by_id(LOGIN_BTN_ID)
        username_input.send_keys(USERNAME)
        password_input.send_keys(PASSWORD)
        login_button.click()
        time.sleep(4)

    def _slider_img_download(self,big_path=IMG_BIG_PATH,block_path=IMG_BLOCK_PATH,CLASSNAME=LOGIN_VERIFY):
        page = bs(self.driver.page_source,'lxml')
        ImgWrapper = page('div',class_=CLASSNAME)
        if ImgWrapper :
            div = ImgWrapper[0]
            imgs = div('img')
            big_src_raw = imgs[0]['src']
            block_src_raw = imgs[-1]['src']
        else:
            return
        download_img(big_src_raw, big_path)
        download_img(block_src_raw, block_path)

    def _get_img_distance(self,big_path=IMG_BIG_PATH,block_path=IMG_BLOCK_PATH,TYPE=1):
        block_img = cv2.imread(block_path, 0)
        big_img = cv2.imread(big_path, 0)
        res = cv2.matchTemplate(  block_img,big_img,cv2.TM_CCOEFF_NORMED)
        min_val, max_val, min_loc, max_loc = cv2.minMaxLoc(res)
        top_left_x = max_loc[0]+SLIDER_DEBUG if TYPE==1 else max_loc[0]
        return top_left_x

    def _validate_and_drag(self,big_path=IMG_BIG_PATH,block_path=IMG_BLOCK_PATH,
                           DRAG=SLIDER_BTN_CLASS,DOWN=VALIDATE_CLASS,TYPE=1):
        img = 1
        while img:
            top_left_x = self._get_img_distance(big_path=big_path,block_path=block_path,TYPE=TYPE)
            drag_and_drop(self.driver, top_left_x, DRAG)
            if TYPE!=1:return
            time.sleep(3)
            soup = bs(self.driver.page_source,'lxml')
            wrapper = soup('div',class_=DRAG) if TYPE==1 else soup('img',class_=DRAG)
            if  wrapper:
                print(soup)
                logger.info(f'识别错误，滑动失败.重新滑动')
                time.sleep(3)
                self._slider_img_download(big_path,block_path,DOWN)
            else:
                img = 0

    def stop(self):
        self.driver.close()
        self.driver.quit()