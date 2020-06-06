#include "Variant.hpp"
#include <iostream>
#include <string>

void Test()
{
	typedef Variant<int, double, std::string, int> cv;

	//����index��ȡ����
	std::cout << typeid(cv::IndexType<1>).name() << std::endl;

	//�������ͻ�ȡ����
	cv v = 10;
	int i = v.GetIndexOf<std::string>(); 
	std::cout << "i = " << i << std::endl;

	//ͨ��һ��lambda����vairant
	v.Visit([&](double i){std::cout << "double: " << i << std::endl; },
		[&](short i){std::cout << "short: " << i << std::endl; },
		[](int i){std::cout << "int: " << i << std::endl; },
		[](std::string i){std::cout << "std::string: " << i << std::endl; } );

	bool emp1 = v.Empty();
	std::cout << v.Type().name() << std::endl;
}

int main(void)
{
	Test();

	system("pause");
	return 0;
}