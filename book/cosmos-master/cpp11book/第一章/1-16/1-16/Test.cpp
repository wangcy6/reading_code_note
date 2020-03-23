#include <iostream>

void func(void)
{
	//...
}

struct Foo
{
	void operator()(void)
	{
		//...
	}
};

struct Bar
{
	using fr_t = void(*)(void);

	static void func(void)
	{
		//...
	}

	operator fr_t(void)
	{
		return func;
	}
};

struct A
{
	int a_;

	void mem_func(void)
	{
		//...
	}
};

int main(void)
{
	void(*func_ptr)(void) = &func;  //����ָ��
	func_ptr();

	Foo foo;  //�º���
	foo();

	Bar bar;  //�ɱ�ת��Ϊ����ָ��������
	bar();

	void(A::*mem_func_ptr)(void) = &A::mem_func;  //���Ա����ָ��
	int A::*mem_obj_ptr = &A::a_;  //���Աָ��

	A aa;
	(aa.*mem_func_ptr)();
	aa.*mem_obj_ptr = 123;

	system("pause");
	return 0;
}