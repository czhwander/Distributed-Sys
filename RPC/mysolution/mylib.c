#define _GNU_SOURCE

#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>
#include <err.h>
#include <stdarg.h>
#include <errno.h>
#include <dirtree.h>
static const int PSIZE = 8;
char *sendMessage(char *msg, int size);
void toConnect();
struct dirtreenode *unmarshall(char *marshalled_tree, int node_number);
struct dirtreenode *form_node(char *marshalled_tree, int *offset);

int sockfd;
// use mask to distinguish whether a fd is from server
int mask = 1 << 30;

// use to check whether this fd is returned by server
int is_valid(int fd) {
	return (fd & mask) != 0;
}
// The following line declares a function pointer with the same prototype as the open function.
int (*orig_open)(const char *pathname, int flags, ...);  // mode_t mode is needed when flags includes O_CREAT
ssize_t (*orig_read)(int fd, void *buf, size_t limit);
int (*orig_close)(int fd);
off_t (*orig_lseek)(int fd, off_t offset, int whence);
int (*orig_stat)(int ver, const char * path, struct stat * stat_buf);
int (*orig_unlink)(const char *path);
ssize_t (*orig_getdirentries)(int fd, char *buf, size_t nbytes , off_t *basep);
struct dirtreenode* (*orig_getdirtree)( const char *path );
void (*orig_freedirtree)( struct dirtreenode* dt );
ssize_t (*orig_write)(int fd, const void *buf, size_t count);

int open(const char *pathname, int flags, ...) {
	printf("this is open\n");
	char *answer;
	mode_t m=0;
	if (flags & O_CREAT) {
		va_list a;
		va_start(a, flags);
		m = va_arg(a, mode_t);
		va_end(a);
	}
	int string_length = strlen(pathname);
	int message_size = string_length + 3 * sizeof(int) + sizeof(mode_t);
	char msg[message_size];
	int type = 1;
	memset(msg,0,message_size);
	// memcpy all needed messages sent to server
	memcpy(msg, &message_size, sizeof(int));
	memcpy(msg + sizeof(int), &type, sizeof(int));
	memcpy(msg + 2 * sizeof(int), &flags, sizeof(int));
	memcpy(msg + 3 * sizeof(int), &m, sizeof(mode_t));
	memcpy(msg + 3 * sizeof(int) + sizeof(mode_t), pathname, string_length);
	// send and get answer from server
	answer = sendMessage(msg, message_size);
	// set the errno
	errno = *(int *)answer;
    int fd;
    memcpy(&fd, answer + sizeof(int),  sizeof(int));
    free(answer);
    // use mask to make fd from server different from others
    return fd | mask;
}

ssize_t read(int fd, void *buf, size_t limit) {
	printf("this is open\n");
	// check whether this fd is get from server before
	if (!is_valid(fd)) {
		return orig_read(fd, buf, limit);
	}
	// unmask the fd
	fd = fd ^ mask;

	char *answer;
	int message_size = 3 * sizeof(int) + sizeof(size_t);
	char msg[message_size];
	int type = 8;
	memset(msg,0,message_size);
	// copy message_size
	memcpy(msg, &message_size, sizeof(int));
	// copy type
	memcpy(msg + sizeof(int), &type, sizeof(int));
	// copy fd
	memcpy(msg + 2 * sizeof(int), &fd, sizeof(int));
	// copy limit
	memcpy(msg + 3 * sizeof(int), &limit, sizeof(size_t));
	//send and get answer back from server
	answer = sendMessage(msg, message_size);
	// set errno
	errno = *(int *)answer;
    ssize_t re;
    memcpy(&re, answer + sizeof(int),  sizeof(ssize_t));
    // in case read return can be smaller than 0
    if (re > 0) memcpy(buf, answer + sizeof(int) + sizeof(ssize_t), re);
    free(answer);
    return re;
}

int close(int fd) {
	// check whether this fd is get from server before
	if (!is_valid(fd)) {
		return orig_close(fd);
	}
	// unmask the fd
	fd = fd ^ mask;
	char *answer;
	int message_size = 3 * sizeof(int);
	char msg[message_size];
	int type = 3;
	memset(msg,0,message_size);
	// assemble the meesage which will be sent
	memcpy(msg, &message_size, sizeof(int));
	memcpy(msg + sizeof(int), &type, sizeof(int));
	memcpy(msg + 2 * sizeof(int), &fd, sizeof(int));
	// send message and get answer back from server
	answer = sendMessage(msg, message_size);
	errno = *(int *)answer;
    int fdd;
    memcpy(&fdd, answer + sizeof(int),  sizeof(int));
    free(answer);
    return fdd;
}

ssize_t write(int fd, const void *buf, size_t limit) {
	// check whether this fd is get from server before
	if (!is_valid(fd)) {
		return orig_write(fd, buf, limit);
	}
	// umask the fd
	fd = fd ^ mask;
	char *answer;
	int message_size = limit + 3 * sizeof(int) + sizeof(size_t);
	char msg[message_size];
	int type = 2;
	memset(msg,0,message_size);
	// assemble the meesage which will be sent
	memcpy(msg, &message_size, sizeof(int));
	memcpy(msg + sizeof(int), &type, sizeof(int));
	memcpy(msg + 2 * sizeof(int), &fd, sizeof(int));
	memcpy(msg + 3 * sizeof(int), &limit, sizeof(size_t));
	memcpy(msg + 3 * sizeof(int) + sizeof(size_t), buf, limit);
	// send message and get answer back from server
	answer = sendMessage(msg, message_size);
	errno = *(int *)answer;
    ssize_t byte_number;
    memcpy(&byte_number, answer + sizeof(int),  sizeof(ssize_t));
    free(answer);
    return byte_number;
}

off_t lseek(int fd, off_t offset, int whence) {
	// check whether this fd is get from server before
	if (!is_valid(fd)) {
		return orig_lseek(fd, offset, whence);
	}
	// unmask the fd
	fd = fd ^ mask;
	char *answer;
	int message_size = 4 * sizeof(int) + sizeof(off_t);
	char msg[message_size];
	int type = 4;
	memset(msg,0,message_size);
	// assemble the meesage which will be sent
	memcpy(msg, &message_size, sizeof(int));
	memcpy(msg + sizeof(int), &type, sizeof(int));
	memcpy(msg + 2 * sizeof(int), &fd, sizeof(int));
	memcpy(msg + 3 * sizeof(int), &offset, sizeof(off_t));
	memcpy(msg + 3 * sizeof(int) + sizeof(off_t), &whence, sizeof(int));
	// send message and get answer back from server
	answer = sendMessage(msg, message_size);
	errno = *(int *)answer;
    off_t off_output;
    memcpy(&off_output, answer + sizeof(int),  sizeof(off_t));
    free(answer);
    return off_output;
}

int __xstat(int ver, const char *path, struct stat *buf) {
	char *answer;
	int string_length = strlen(path);
	int message_size = 3 * sizeof(int) + string_length;
	char msg[message_size];
	int type = 5;
	memset(msg,0,message_size);
	// copy message_size
	memcpy(msg, &message_size, sizeof(int));
	// copy type
	memcpy(msg + sizeof(int), &type, sizeof(int));
	// copy ver
	memcpy(msg + 2 * sizeof(int), &ver, sizeof(int));
	// copy path
	memcpy(msg + 3 * sizeof(int), path, string_length);
	// send message and get answer back from server
	answer = sendMessage(msg, message_size);
	errno = *(int *)answer;
    int xstat_return;
    memcpy(&xstat_return, answer + sizeof(int),  sizeof(int));
    memcpy(buf, answer + 2 * sizeof(int), sizeof(struct stat));
    free(answer);
    return xstat_return;
}

int unlink(const char *path) {
	char *answer;
	int string_length = strlen(path);
	int message_size = 2 * sizeof(int) + string_length;
	char msg[message_size];
	int type = 6;
	memset(msg,0,message_size);
	// copy message_size
	memcpy(msg, &message_size, sizeof(int));
	// copy type
	memcpy(msg + sizeof(int), &type, sizeof(int));
	// copy path
	memcpy(msg + 2 * sizeof(int), path, string_length);
	// send message and get answer back from server
	answer = sendMessage(msg, message_size);
	errno = *(int *)answer;
    int unlike_return;
    memcpy(&unlike_return, answer + sizeof(int),  sizeof(int));
    free(answer);
    return unlike_return;
}

ssize_t getdirentries(int fd, char *buf, size_t limit , off_t *offset) {
	// check whether this fd is get from server before
	if (!is_valid(fd)) {
		return orig_getdirentries(fd, buf, limit, offset);
	}
	// unmask the fd
	fd = fd ^ mask;
    char *answer;
	int message_size = 3 * sizeof(int) + sizeof(size_t) + sizeof(off_t);
	char msg[message_size];
	int type = 7;
	memset(msg,0,message_size);
	// copy message_size
	memcpy(msg, &message_size, sizeof(int));
	// copy type
	memcpy(msg + sizeof(int), &type, sizeof(int));
	// copy fd
	memcpy(msg + 2 * sizeof(int), &fd, sizeof(int));
	// copy limit
	memcpy(msg + 3 * sizeof(int), &limit, sizeof(size_t));
	// copy offset
	memcpy(msg + 3 * sizeof(int) + sizeof(size_t), offset, sizeof(off_t));
	// send message and get answer back from server
	answer = sendMessage(msg, message_size);
	errno = *(int *)answer;
    ssize_t re;
    memcpy(&re, answer + sizeof(int),  sizeof(ssize_t));
    // return value can be smaller than 0
    if (re > 0) memcpy(buf, answer + sizeof(int) + sizeof(ssize_t), re);
    free(answer);
    return re;
}

struct dirtreenode* getdirtree(const char *path) {
    char *answer;
	int string_length = strlen(path);
	int message_size = 2 * sizeof(int) + string_length;
	char msg[message_size];
	int type = 9;
	memset(msg,0,message_size);
	// copy message_size
	memcpy(msg, &message_size, sizeof(int));
	// copy type
	memcpy(msg + sizeof(int), &type, sizeof(int));
	// copy path
	memcpy(msg + 2 * sizeof(int), path, string_length);
	// send message and get answer back from server
	answer = sendMessage(msg, message_size);
	errno = *(int *)answer;
	// receive the length of the message and the number of nodes
    int length;
    int node_number;
    memcpy(&length, answer + sizeof(int),  sizeof(int));
    memcpy(&node_number, answer + 2 * sizeof(int),  sizeof(int));
    struct dirtreenode *root = NULL;
    // if node number is 0, we don't need to unmarshall any node
    if (node_number > 0) root = unmarshall(answer + 3 * sizeof(int), node_number);
    free(answer);
    return root;
}

// freedirtree don't need to be sent to server, just free the local tree will be fine
// server's tree will be freed after being sent to client
void freedirtree(struct dirtreenode* tree) {
    return orig_freedirtree(tree);
}

// This function is automatically called when program is started
void _init(void) {
	// set function pointer orig_open to point to the original open function
	orig_open = dlsym(RTLD_NEXT, "open");
	orig_close = dlsym(RTLD_NEXT, "close");
	orig_write = dlsym(RTLD_NEXT, "write");
	orig_read = dlsym(RTLD_NEXT, "read");
    orig_lseek = dlsym(RTLD_NEXT, "lseek");
    orig_stat  = dlsym(RTLD_NEXT, "__xstat");
    orig_unlink = dlsym(RTLD_NEXT, "unlink");
    orig_getdirentries = dlsym(RTLD_NEXT, "getdirentries");
    orig_getdirtree = dlsym(RTLD_NEXT, "getdirtree");
    orig_freedirtree = dlsym(RTLD_NEXT, "freedirtree");
    // connect to server and record the sockfd
    toConnect();
}

// this function will call after client finish his own work
// and it will send signal to server, so server can exit the sub-process
void _fini(void) {
	int message_size = 3 * sizeof(int);
	char msg[message_size];
	int type = 10;
	memset(msg,0,message_size);
	// message_size;
	memcpy(msg, &message_size, sizeof(int));
	// type
	memcpy(msg + sizeof(int), &type, sizeof(int));
	send(sockfd, msg, message_size, 0);
	orig_close(sockfd);
}

// this function used to sen message
char *sendMessage(char *msg, int size) {
	int rv;
	//send message to server
	send(sockfd, msg, size, 0);	// send message; should check return value
	// get message back
	char *buf = malloc(sizeof(int));
	char *numbuf = malloc(sizeof(int));
	int count = 0;
	
	// firstly, just receive 4 bytes which is the size of whole message will be received this time
	while ((rv = recv(sockfd, buf, sizeof(int), 0)) > 0) {
		memcpy(numbuf + count, buf, rv);
		count += rv;
		if (count >= 4) break;
	}
	int message_size = *(int *)numbuf;
	free(buf);
	free(numbuf);
	buf = malloc(message_size - sizeof(int));
	char *content = malloc(message_size - sizeof(int) + 1);
	count = 0;
	// receive left message, in case of the message will not arrive at the same time, use a while loop to receive
	while ((rv = recv(sockfd, buf, message_size - sizeof(int), 0)) > 0) {
		memcpy(content + count, buf, rv);
		count += rv;
		if (count >= message_size - sizeof(int)) break;
	}
	free(buf);
	if (rv<0) err(1,0);			// in case something went wrong
	// end of the string 
	content[message_size - sizeof(int) + 1] = 0;
	// close socket
	return content;
}

// this function is used to connect server at the beginning
void toConnect() {
	char *serverip;
	char *serverport;
	unsigned short port;
	int rv;
	struct sockaddr_in srv;
	// Get environment variable indicating the ip address of the server
	serverip = getenv("server15440");
	if (!serverip) {
		serverip = "127.0.0.1";
	}
	// Get environment variable indicating the port of the server
	serverport = getenv("serverport15440");
	if (!serverport) {
		serverport = "15440";
	}
	port = (unsigned short)atoi(serverport);
	// Create socket
	sockfd = socket(AF_INET, SOCK_STREAM, 0);	// TCP/IP socket
	if (sockfd<0) err(1, 0);			// in case of error
	// setup address structure to point to server
	memset(&srv, 0, sizeof(srv));			// clear it first
	srv.sin_family = AF_INET;			// IP family
	srv.sin_addr.s_addr = inet_addr(serverip);	// IP address of server
	srv.sin_port = htons(port);			// server port
	rv = connect(sockfd, (struct sockaddr*)&srv, sizeof(struct sockaddr));
	if (rv<0) err(1,0);
}

// used to unmarshall a tree from the message
struct dirtreenode *unmarshall(char *marshalled_tree, int node_number) {
	// Queue of node reference, used to rebuild the tree
	char **queue = malloc(node_number * PSIZE);
	int queue_size = 0;
	int start = 0;
	int offset = 0;
	// contruct the root node
	struct dirtreenode *root = form_node(marshalled_tree, &offset);
	// enqueue the first node
	memcpy(queue + queue_size, &root, PSIZE);
	queue_size++;

	while (queue_size != 0) {
		int n = queue_size;
		int i;
		for (i = 0; i < n; i++) {
			// dequeue a node father
			struct dirtreenode *father = (struct dirtreenode *)(*(queue + start));
			start++;
			queue_size--;
			int j;
			for (j = 0; j < father->num_subdirs; j++) {
				// form the son nodes of father
				struct dirtreenode *son = form_node(marshalled_tree, &offset);
				memcpy(father->subdirs + j, &son, PSIZE);

				// enqueue all son nodes
				memcpy(queue + start + queue_size, &son, PSIZE);
				queue_size++;
			}
		}
	}
	free(queue);
	return root;
}

// this function is used to form a single node from the message received
struct dirtreenode *form_node(char *marshalled_tree, int *offset) {
	struct dirtreenode *new_node = malloc(sizeof(struct dirtreenode));
	int string_length = strlen(marshalled_tree + *offset);
	// copy the name of a node
	new_node->name = malloc(string_length + 1);
	memcpy(new_node->name, marshalled_tree + *offset, string_length + 1);
	*offset += string_length + 1;
	// copy the number of son of a node
	int number_of_children = *(int *)(marshalled_tree + *offset);
	memcpy(&new_node->num_subdirs, &number_of_children, sizeof(int));
	*offset += sizeof(int);

	// if this node have sons, malloc space for his sons' pointers array
	if (number_of_children != 0) new_node->subdirs = malloc(PSIZE * number_of_children);
	return new_node;
}
