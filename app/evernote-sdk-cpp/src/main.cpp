#include <iostream>
#include "NoteStore.h"
using  namespace std;


int main(void) {
    
	string developerToken = "S=s39:U=7d880d:E=170b2df6dd3:C=1708ed2e9c0:P=1cd:A=en-devtoken:V=2:H=176b3a87f2eb3244006c04768a069d5a";

	// Set up the NoteStore client 
	EvernoteAuth evernoteAuth = new EvernoteAuth(EvernoteService.SANDBOX, developerToken);
	ClientFactory factory = new ClientFactory(evernoteAuth);
	NoteStoreClient noteStore = factory.createNoteStoreClient();

	// Make API calls, passing the developer token as the authenticationToken param
	//List<Notebook> notebooks = noteStore.listNotebooks();

	//for (Notebook notebook : notebooks) {
	//	System.out.println("Notebook: " + notebook.getName());
	//}
    return 0;
}