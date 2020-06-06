#include <iostream>
#include <functional>

class A
{
public:
	int i_ = 0;

	void output(int x, int y)
	{
		std::cout << x << " " << y << std::endl;
	}
};

int main(void)
{
	A a;
	std::function<void(int, int)> fr =
		std::bind(&A::output, &a, std::placeholders::_1, std::placeholders::_2);
	fr(1, 2);  //��� 1 2

	std::function<int&(void)> fr_i = std::bind(&A::i_, &a);  //vs13��bug���󶨳�Ա����Ҫ����
	fr_i() = 123;

	std::cout << a.i_ << std::endl;  //��� 123

	system("pause");
	return 0;
}