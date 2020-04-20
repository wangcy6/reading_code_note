#coding=utf-8
from component.toutiao import TTBot
# def ass(res):
#     print(res)
#     http://www.bianbingdang.com/article_detail/148.html
#     python默认的成员变量、成员函数都是public的
if __name__ == '__main__':
	bot = TTBot()
	account = bot.account
	account.login()
	print(account.get_login_log(page=2))
	print(account.post_article('I robot sssss ',)
