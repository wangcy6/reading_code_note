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
//g++ -g -std=c++11 algorithm_sort.cpp
int main()
{
	vector<int> myvector{5, 1, 21, 13, 8, 15, 6};
	
  std::sort (myvector.begin(), myvector.end(), myobject);
	
	for (auto n : myvector) 
	{
		cout << n << " ";
	}
	cout << endl;
	
	return 0;
}
