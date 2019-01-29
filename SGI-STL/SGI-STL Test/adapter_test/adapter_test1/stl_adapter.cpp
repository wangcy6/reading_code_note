#include <iostream>
#include <string>
#include <iterator>
#include <vector>
#include <algorithm>

using namespace std;

int main()
{   

    //////////////////1 ///////////////////////////
	vector<int> myvector;
	
	back_insert_iterator< vector<int> > insert=back_inserter(myvector);

	insert = 1;  //push_back
	insert = 2; //push_back
	insert = 3; //push_back
	insert = 4; //push_back
    
	vector<int>::iterator iter = myvector.begin();
	
	for ( ; iter != myvector.end(); ++iter)
		cout << *iter << endl;
	
	
    /**
	output:
	1
	2
	3
	
	template <class _Container>
	class back_insert_iterator 
	{
		protected:
		_Container* container;
		explicit back_insert_iterator(_Container& __x) : container(&__x) {} //声明为explicit的构造函数不能在隐式转换中使用。

		back_insert_iterator<_Container>&
		operator=(const typename _Container::value_type& __value) 
		{ 
			container->push_back(__value);
			return *this; //对迭代器适配器的赋值变为了对容器的插入操作。
		}
	};
	
		template <class _Container>
		inline back_insert_iterator<_Container> back_inserter(_Container& __x) 
		{
			return back_insert_iterator<_Container>(__x); //创建一个临时对象
		}
	**/
	///////////////////2////////////////////
	
	int a[] = {9,5,4,8,3,6,7};
	reverse_iterator<int*> reverite(a+7); //pointer 
	
	cout << *reverite++ << endl; //7
	cout << *reverite++ << endl;//6
	cout << *reverite++ << endl;//3



   ///3333333333

   // 计算容器中小于等于3的元素个数
	cout << count_if(myvector.begin(), myvector.end(), bind2nd(less_equal<int>(), 3));

	return 0;
}


