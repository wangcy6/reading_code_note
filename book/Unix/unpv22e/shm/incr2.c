#include	"unpipc.h"

#define	SEM_NAME	"mysem"

<<<<<<< HEAD
int main(int argc, char **argv)
=======
int
main(int argc, char **argv)
>>>>>>> 4ecd10e86f9964ffc8c2e184effdde21375472a8
{
	int		fd, i, nloop, zero = 0;
	int		*ptr;
	sem_t	*mutex;

	if (argc != 3)
		err_quit("usage: incr2 <pathname> <#loops>");
	nloop = atoi(argv[2]);

<<<<<<< HEAD
	/* 4open file, initialize to 0, map into memory */
=======
		/* 4open file, initialize to 0, map into memory */
>>>>>>> 4ecd10e86f9964ffc8c2e184effdde21375472a8
	fd = Open(argv[1], O_RDWR | O_CREAT, FILE_MODE);
	Write(fd, &zero, sizeof(int));
	ptr = Mmap(NULL, sizeof(int), PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
	Close(fd);

<<<<<<< HEAD
	/* 4create, initialize, and unlink semaphore */
=======
		/* 4create, initialize, and unlink semaphore */
>>>>>>> 4ecd10e86f9964ffc8c2e184effdde21375472a8
	mutex = Sem_open(Px_ipc_name(SEM_NAME), O_CREAT | O_EXCL, FILE_MODE, 1);
	Sem_unlink(Px_ipc_name(SEM_NAME));

	setbuf(stdout, NULL);	/* stdout is unbuffered */
	if (Fork() == 0) {		/* child */
		for (i = 0; i < nloop; i++) {
			Sem_wait(mutex);
			printf("child: %d\n", (*ptr)++);
			Sem_post(mutex);
		}
		exit(0);
	}

		/* 4parent */
	for (i = 0; i < nloop; i++) {
		Sem_wait(mutex);
		printf("parent: %d\n", (*ptr)++);
		Sem_post(mutex);
	}
	exit(0);
}
