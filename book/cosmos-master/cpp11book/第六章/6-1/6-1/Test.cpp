#define _CRT_SECURE_NO_WARNINGS

#include "Timer.hpp"

#include <chrono>
#include <iostream>
#include <iomanip>
#include <ctime>

void fun()
{
	std::cout << "hello world" << std::endl;
}

void Test()
{
	std::cout << "\nTest()\n";

	Timer t; //��ʼ��ʱ
	fun();

	std::cout << t.elapsed_seconds() << std::endl; //��ӡfun������ʱ������
	std::cout << t.elapsed_nano() << std::endl; //��ӡ����
	std::cout << t.elapsed_micro() << std::endl; //��ӡ΢��
	std::cout << t.elapsed() << std::endl; //��ӡ����
	std::cout << t.elapsed_seconds() << std::endl; //��ӡ��
	std::cout << t.elapsed_minutes() << std::endl; //��ӡ����
	std::cout << t.elapsed_hours() << std::endl; //��ӡСʱ
}


int main(void)
{
	Test();

	system("pause");
	return 0;
}