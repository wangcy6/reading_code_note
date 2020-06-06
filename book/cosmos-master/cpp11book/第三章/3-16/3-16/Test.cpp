#include "ScopeGuard.hpp"

#include <iostream>
#include <functional>

void TestScopeGuard()
{
	std::function < void()> f = []()
	{ std::cout << "cleanup from unnormal exit" << std::endl; };
	//�����˳�
	{
		auto gd = MakeGuard(f);
		//...
		gd.Dismiss();  //����ǰ��������������������Դ�����������˳���
	}

	//�쳣�˳�
	try
	{
		auto gd = MakeGuard(f);
		//...
		throw 1;
	}
	catch (...)
	{
		std::cout << "������һ���쳣����\n\n";
	}

	//�������˳�
	{
		auto gd = MakeGuard(f);
		return;  //�������˳���ʾ��Դ��û�����أ�������ScopeGuard�Զ�����
		//...
	}
}


int main(void)
{
	TestScopeGuard();

	system("pause");
	return 0;
}
