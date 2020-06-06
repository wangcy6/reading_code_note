#include <iostream>
#include <type_traits>
#include <memory>

template <class T>
struct Construct
{
	typedef typename std::remove_reference<T>::type U;
	Construct() : m_ptr(new U){}

	typename std::add_lvalue_reference<U>::type Get() const
	{
		*m_ptr = 100;  //����ӵ�һ�У��Է�*m_ptr�Ǹ�δ�����ֵ
		return *m_ptr.get();
	}

private:
	std::unique_ptr<U> m_ptr;
};

int main(void)
{
	Construct<int> c;
	int a = c.Get();

	std::cout << a << std::endl;

	system("pause");
	return 0;
}