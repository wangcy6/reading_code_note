
import time
import json
from selenium import webdriver
class TouTiao:
    def __init__(self):
        self.cookies = None
        self.browser = webdriver.Chrome()
    def set_cookies(self):
        with open('cookies.json') as f:
            self.cookies = json.loads(f.read())
        for cookie in self.cookies:
            self.browser.add_cookie(cookie)
    def create_session(self):
        self.browser.get("https://mp.toutiao.com")
        if self.cookies is None:
            self.set_cookies()
        time.sleep(1)
        self.browser.get("https://mp.toutiao.com/profile_v3/index")
    def forward_wei(self, content):
        """
        跳转微头条
        :return:
        """
        self.browser.get("https://mp.toutiao.com/profile_v3/weitoutiao/publish")
        time.sleep(1)
        # 微头条内容框
        weitoutiao_content = self.browser.find_element_by_css_selector(
            "div > div.garr-container-white.weitoutiao-index-zone > div > div:nth-child(1) > textarea")
        weitoutiao_content.send_keys(content)
        # 微头条发布按钮
        weitoutiao_send = self.browser.find_element_by_css_selector(
            "div > div.garr-container-white.weitoutiao-index-zone > div > button")
        weitoutiao_send.click()
    def login(self):
        self.browser.get("https://mp.toutiao.com/profile_v3/index")
        # 点击登陆按钮
        login = self.browser.find_element_by_css_selector('body > div > div.carousel > div.page.page-1 > div > img.i3')
        login.click()
        time.sleep(3)
        # 填写手机号
        phone = self.browser.find_element_by_id('user-name')
        phone.send_keys('19991320539')
        # 获取验证码
        self.browser.find_element_by_id('mobile-code-get').click()
        verfiy_code_input = input("请输入验证码:")
        # 验证码输入框
        mobile_code = self.browser.find_element_by_id('mobile-code')
        mobile_code.send_keys(verfiy_code_input)
        # 登陆
        self.browser.find_element_by_id('bytedance-SubmitStatic').click()
        time.sleep(5)
        cookies = self.browser.get_cookies()
        with open('cookies.json', 'w') as f:
            self.cookies = json.loads(f.write(json.dumps(cookies)))
        print(cookies, "登陆成功")
    def close(self):
        self.browser.close()
if __name__ == '__main__':
    tou_tiao = TouTiao()
    tou_tiao.create_session()
    //tou_tiao.forward_wei('<br/>test')