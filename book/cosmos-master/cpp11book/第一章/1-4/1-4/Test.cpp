#include <iostream>

class Foo{};

int& func_int_r(void);    //��ֵ��lvalue���ɼ����Ϊ��Ѱֵַ��
int&& func_int_rr(void);  //xֵ��xvalue����ֵ���ñ�����һ��xvalue��
int func_int(void);       //����ֵ��pvalue��

const int& func_cint_r(void);         //��ֵ
const int&& func_cint_rr(void);       //xֵ
const int func_cint(void);            //����ֵ

const Foo func_cfoo(void);  //����ֵ

int main(void)
{
	int x = 0;

	decltype(func_int_r()) a1 = x;    //a1 -> int&
	decltype(func_int_rr()) b1 = 0;   //b1 -> int&&
	decltype(func_int()) c1 = 0;      //c1 -> int

	decltype(func_cint_r()) a2 = x;    //a2 -> const int&
	decltype(func_cint_rr()) b2 = 0;   //b2 -> const int&&
	decltype(func_cint()) c2 = 0;      //c2 -> int

	decltype(func_cfoo()) ff = Foo();  //ff -> Foo

	system("pause");
	return 0;
}