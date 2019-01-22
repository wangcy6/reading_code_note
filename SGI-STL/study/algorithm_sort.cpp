#include <algorithm>
#include <iostream>
#include <vector>

using namespace std;

struct myclass {
  bool operator()(int i,int j) 
  { 
	return  i<=j;
  }
} myobject;

bool compare(int a, int b)
{
    return a >= b;
}
//g++ -g -std=c++11 algorithm_sort.cpp
int main()
{
   vector<int> myvector;
   
    for (int i = 0; i < 17; i++)
    {
        myvector.push_back(i);
	    //myvector.push_back(0);
    }
 
	
   std::sort (myvector.begin(), myvector.end(), compare);
	
	for (auto n : myvector) 
	{
		cout << n << " ";
	}
	cout << endl;
	
	
	
	//
	int total = 0;
	std::for_each(myvector.begin(), myvector.end(), [&total](int x) { total += x; });
	std::cout << total;
	

	return 0;
}
