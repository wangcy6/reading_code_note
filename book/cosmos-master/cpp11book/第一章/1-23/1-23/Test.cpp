#include <iostream>

class A
{
	int i_ = 0;

	void func(int x, int y)
	{
		auto x1 = []{return i_; };  //error,û�в����ⲿ����
		auto x2 = [=]{return i_ + x + y; };
		auto x3 = [&]{return i_ + x + y; };
		auto x4 = [this]{return i_; };
		auto x5 = [this]{return i_ + x + y; };  //error,û�в���x��y
		auto x6 = [this, x, y]{return i_ + x + y; };
		auto x7 = [this]{return i_++; };
	}
};

int main(void)
{
	{
		int a = 0;
		int b = 1;
		auto f1 = []{return a; };  //error,û�в����ⲿ���� 
		auto f2 = [&]{return a++; };
		auto f3 = [=]{return a; };
		auto f4 = [=]{return a++; };  //error,a���Ը��Ʒ�ʽ����ģ��޷��޸�
		auto f5 = [a]{return a + b; };  //error,û�в������b
		auto f6 = [a, &b]{return a + (b++); };
		auto f7 = [=, &b]{return a + (b++); };
	}

	system("pause");
	return 0;
}