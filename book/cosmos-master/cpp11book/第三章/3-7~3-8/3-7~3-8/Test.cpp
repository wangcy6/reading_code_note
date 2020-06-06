#include "Lazy.hpp"

#include <iostream>
#include <memory>

struct BigObject
{
	BigObject()
	{
		std::cout << "lazy load big object" << std::endl;
	}
};

struct MyStruct
{
	MyStruct()
	{
		m_obj = lazy([]{return std::make_shared<BigObject>(); });
	}

	void Load()
	{
		m_obj.Value();
	}

	Lazy<std::shared_ptr<BigObject>> m_obj;
};

int Foo(int x)
{
	return x * 2;
}

void TestLazy()
{
	//����������ͨ����
	int y = 4;
	auto lazyer1 = lazy(Foo, y);
	std::cout << lazyer1.Value() << std::endl;

	//����������lamda
	Lazy<int> lazyer2 = lazy([]{return 12; });
	std::cout << lazyer2.Value() << std::endl;

	//��������fucntion
	std::function < int(int) > f = [](int x){return x + 3; };
	auto lazyer3 = lazy(f, 3);
	std::cout << lazyer3.Value() << std::endl;

	//�ӳټ��ش����
	MyStruct t;
	t.Load();
}


int main(void)
{
	TestLazy();

	system("pause");
	return 0;
}