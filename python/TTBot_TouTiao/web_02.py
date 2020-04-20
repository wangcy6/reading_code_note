import time
import json
import pickle

#webdriver是selenium模块的一个类
from selenium import webdriver
class TouTiao:
    def __init__(self):
        self.cookies =pickle.load(open("cookies.pkl", "rb"))
        #采用谷歌浏览器
        chrome_opt = webdriver.ChromeOptions()
        chrome_opt.add_argument('--disable-gpu')
        path = r"D:\local\Programs\Python\Python38-32\chromedriver.exe"
        self.browser = webdriver.Chrome(executable_path=path,chrome_options=chrome_opt)
    def set_cookies(self):
        with open('cookies.json') as f:
            self.cookies = json.loads(f.read())
        for cookie in self.cookies:
            self.browser.add_cookie(cookie)
    def loginWithCookies(self):
        """
        跳转微头条
        :return:
        """
        if self.cookies is None:
            self.login()
        self.browser.get("https://www.toutiao.com/")
        self.cookies=pickle.load(open("cookies.pkl", "rb"))
        for cookie in self.cookies:
            if 'expiry' in cookie:
              cookie['expiry'] = int(cookie['expiry'])
            self.browser.add_cookie(cookie)
        # 微头条内容框
        #self.browser.get("https://mp.toutiao.com/profile_v3/weitoutiao/publish")
        #time.sleep(3)
        #self.browser.refresh()
    def forward_wei(self, content):
        """
        跳转微头条
        :return:
        """
        # 微头条内容框
        self.browser.get("https://mp.toutiao.com/profile_v3/weitoutiao/publish")
        self.browser.implicitly_wait(10)
        # 微头条内容框
       #weitoutiao_content = self.browser.find_element_by_css_selector(
        #    "div > div.garr-container-white.weitoutiao-index-zone > div > div:nth-child(1) > textarea")
        # id="publish-text-area" class="publish-box"
        weitoutiao_content = self.browser.find_element_by_id("publish-text-area")
        weitoutiao_content.send_keys(content)
        # 微头条发布按钮
        #weitoutiao_send = self.browser.find_element_by_css_selector(
        #    "div > div.garr-container-white.weitoutiao-index-zone > div > button")
        #https://www.cnblogs.com/bzdmz/p/10325152.html
        #weitoutiao_send = self.browser.find_element_by_class("byte-btn byte-btn-primary byte-btn-size-default byte-btn-shape-square publish-content")
        weitoutiao_send = self.browser.find_elements_by_xpath("//div[@class='footer-wrap']/div/button")
        print(weitoutiao_send)
        #weitoutiao_send = self.browser.find_element_by_xpath("//button[1]")
        weitoutiao_send.click()
    def login(self):
       self.browser.get("https://mp.toutiao.com")
       time.sleep(30)
       cookies = self.browser.get_cookies()
       pickle.dump(cookies, open("cookies.pkl","wb"))
       print(cookies, "登陆成功")
    def close(self):
        self.browser.close()
    def SearchMsg(self):
       word=self.browser.find_element_by_xpath('//*[@id="rightModule"]/div[1]/div/div/div/input')
       word.send_keys("复盘 早睡早起")
       time.sleep(2)
       self.browser.find_element_by_xpath('//*[@id="rightModule"]/div[1]/div/div/div/div/button/span').click()
       time.sleep(5)
if __name__ == '__main__':
    tou_tiao = TouTiao()
    tou_tiao.loginWithCookies()
   #tou_tiao.SearchMsg()
    #tou_tiao.create_session()
    tou_tiao.forward_wei('复盘 早睡早起')