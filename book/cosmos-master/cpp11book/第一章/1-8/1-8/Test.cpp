#include <iostream>

class Foo
{
public:
	Foo(int){}

private:
	Foo(const Foo&);
};

int main(void)
{
	Foo a1(123);
	Foo a2 = a1;  //error C2248: ��Foo::Foo��: �޷����� private ��Ա(�ڡ�Foo����������)
	Foo a3 = { 123 };
	Foo a4{ 123 };

	int a5 = { 3 };
	int a6 { 3 };

	system("pause");
	return 0;
}