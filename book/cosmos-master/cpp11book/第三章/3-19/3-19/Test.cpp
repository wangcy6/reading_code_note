#include <iostream>
#include <tuple>
#include <type_traits>
#include <string>

namespace detail
{
	//���ڿ�ת��������ֵ��ֱ�ӱȽ�
	template <typename T, typename U>
	typename std::enable_if<std::is_convertible<T, U>::value || 
	    std::is_convertible<U, T>::value, bool>::type
		compare(T t, U u)
	{
			return t == u;
	}

	//���ܻ���ת������ֱ�ӷ���false
	bool compare(...)
	{
		return false;
	}


	//����ֵ��������
	template<int I, typename T, typename... Args>
	struct find_index
	{
		static int call(std::tuple<Args...> const& t, T&& val)
		{
			return (compare(std::get<I - 1>(t), val) ? I - 1 :
				find_index<I - 1, T, Args...>::call(t, std::forward<T>(val)));
		}
	};

	template<typename T, typename... Args>
	struct find_index<0, T, Args...>
	{
		static int call(std::tuple<Args...> const& t, T&& val)
		{
			return compare(std::get<0>(t), val) ? 0 : -1;
		}
	};
}

template<typename T, typename... Args>
int find_index(std::tuple<Args...> const& t, T&& val)
{
	return detail::find_index<sizeof...(Args), T, Args...>::call(t, std::forward<T>(val));
}

int main(void)
{
	std::tuple<int, double, std::string> tp = std::make_tuple(1, 2, std::string("OK"));
	int index = find_index(tp, std::string("OK"));

	std::cout << index << std::endl;

	system("pause");
	return 0;
}