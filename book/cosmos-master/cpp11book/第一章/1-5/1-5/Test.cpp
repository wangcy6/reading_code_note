#include <iostream>
#include <vector>

template <class ContainerT>
class Foo
{
	typename ContainerT::iterator it_;  //���Ͷ������������
	//������ const ContainerT ��ͨ��������ʹ������it_���壺
	//decltype(std::declval<ContainerT>().begin()) it_;
public:
	void func(ContainerT& container)
	{
		it_ = container.begin();
	}

	//...
};

int main(void)
{
	typedef const std::vector<int> container_t;  //������const���ԣ�����ᱨһ��Ѵ�����Ϣ
	container_t arr;

	Foo<container_t> foo;
	foo.func(arr);

	system("pause");
	return 0;
}