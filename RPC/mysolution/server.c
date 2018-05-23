#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <string.h>
#include <unistd.h>
#include <err.h>
#include <fcntl.h>
#include <errno.h>
#include <dirtree.h>
static const int PSIZE = 8;
void calculate(struct dirtreenode *root, int *number, int *size);
char *tree_marshall(struct dirtreenode *root, int *length, int *node_number);

int main(int argc, char**argv) {
	char *serverport;
	unsigned short port;
	int sockfd, sessfd, rv, type, size, count, pro;
	char buf[sizeof(int)];
	struct sockaddr_in srv, cli;
	socklen_t sa_size;

	// Get environment variable indicating the port of the server
	serverport = getenv("serverport15440");
	if (serverport) port = (unsigned short)atoi(serverport);
	else port=15440;

	// Create socket
	sockfd = socket(AF_INET, SOCK_STREAM, 0);	// TCP/IP socket
	if (sockfd<0) err(1, 0);			// in case of error

	// setup address structure to indicate server port
	memset(&srv, 0, sizeof(srv));			// clear it first
	srv.sin_family = AF_INET;			// IP family
	srv.sin_addr.s_addr = htonl(INADDR_ANY);	// don't care IP address
	srv.sin_port = htons(port);			// server port

	// bind to our port
	rv = bind(sockfd, (struct sockaddr*)&srv, sizeof(struct sockaddr));
	if (rv<0) err(1,0);

	// start listening for connections
	rv = listen(sockfd, 5);
	if (rv<0) err(1,0);

	// main server loop, handle clients one at a time, quit after 10 clients
	while(1) {

		// wait for next client, get session socket
		sa_size = sizeof(struct sockaddr_in);
		sessfd = accept(sockfd, (struct sockaddr *)&cli, &sa_size);
		if (sessfd<0) err(1,0);

		// fork a new sub process
		pro = fork();
		if (pro==0) { // inside the sub process
			close(sockfd);
			while(1) {
				count = 0;

				// firstly, just receive 4 bytes which is the size of whole message will be received this time
				char *numbuf = malloc(sizeof(int));
				while ((rv = recv(sessfd, buf, 4, 0)) > 0) {
					memcpy(numbuf + count, buf, rv);
					count += rv;
					if (count >= 4) break;
				}
				size = *(int *)numbuf;
				free(numbuf);

				char *buffer = malloc(size - sizeof(int));
				char *content = malloc(size - sizeof(int));
				count = 0;
				// receive left message, in case of the message will not arrive at the same time, use a while loop to receive
				while ( (rv=recv(sessfd, buffer, size - sizeof(int), 0)) > 0) {
					memcpy(content + count, buffer, rv);
					count += rv;
					if (count >= (size - sizeof(int))) break;
				}
				free(buffer);
				type = *(int *)content;
				if (type == 1) { // open
					// copy info from the message received
					int flag = *(int *)(content + sizeof(int));
					mode_t m = *(mode_t *)(content + 2 * sizeof(int));
					int string_length = size - 3 * sizeof(int) - sizeof(mode_t);
					char *path = malloc(string_length + 1);
					memcpy(path, (content + 2 * sizeof(int) + sizeof(mode_t)), string_length);
					// end of a string
					path[string_length] = 0;
					// call orig open function
					int fd = open(path, flag, m);
					// form the return message
					int message_size = 3 * sizeof(int);
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &fd, sizeof(int));
					// send return message
					send(sessfd, msg, message_size, 0);
					free(msg);
					free(path);
				} else if (type == 2) { // write
					// copy info from the message received
					int fd = *(int *)(content + sizeof(int));
					size_t limit = *(size_t *)(content + 2 * sizeof(int));
					int string_length = size - 3 * sizeof(int) - sizeof(size_t);
					char *string = malloc(string_length + 1);
					memcpy(string, content + 2 * sizeof(int) + sizeof(size_t), string_length);
					// end of a string
					string[string_length] = 0;
					// call orig write function
					ssize_t re = write(fd, string, limit);
					// form the return message
					int message_size = 2 * sizeof(int) + sizeof(ssize_t);
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &re, sizeof(ssize_t));
					//send return message
					send(sessfd, msg, message_size, 0);
					free(string);
					free(msg);
				} else if (type == 3) { // close
					// copy info from the message received
					int fd = *(int *)(content + sizeof(int));
					// call orig close function
					int re = close(fd);
					// form the return meesage
					int message_size = 3 * sizeof(int);
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &re, sizeof(int));
					// send return message
					send(sessfd, msg, message_size, 0);
					free(msg);
				} else if (type == 4) { // lseek
					// copy info from the message received
					int fd = *(int *)(content + sizeof(int));
					off_t offset = *(off_t *)(content + 2 * sizeof(int));
					int whence = *(int *)(content + 2 * sizeof(int) + sizeof(off_t));

					// call the orig lseek function
					off_t re = lseek(fd, offset, whence);
					// form the return message
					int message_size = 2 * sizeof(int) + sizeof(off_t);
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &re, sizeof(off_t));
					// send return message
					send(sessfd, msg, message_size, 0);
					free(msg);
				} else if (type == 5) { // __xstat
					// copy info from the message received
					int ver = *(int *)(content + sizeof(int));
					int string_length = size - 3 * sizeof(int);
					char *path = malloc(string_length + 1);
					memcpy(path, content + 2 * sizeof(int), string_length);
					path[string_length] = 0;
					struct stat *tembuf = (struct stat *)malloc(sizeof(struct stat));
					// call orig __xstat function
					int re = __xstat(ver, path, tembuf);
					// form the return function
					int message_size = 3 * sizeof(int) + sizeof(struct stat);
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &re, sizeof(int));
					memcpy(msg + 3 * sizeof(int), &tembuf, sizeof(struct stat));
					// send the return function
					send(sessfd, msg, message_size, 0);
					free(tembuf);
					free(path);
					free(msg);
				} else if (type == 6) { // unlink
					// copy info from the message received
					int string_length = size - 2 * sizeof(int);
					char *path = malloc(string_length + 1);
					memcpy(path, content + sizeof(int), string_length);
					path[string_length] = 0;
					// call orig unlink function
					int re = unlink(path);
					// form the return message
					int message_size = 3 * sizeof(int);
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &re, sizeof(int));
					// send return message
					send(sessfd, msg, message_size, 0);
					free(path);
					free(msg);
				} else if (type == 7) { // getdirentries
					// copy info from the message received
					int fd = *(int *)(content + sizeof(int));
					size_t limit = *(size_t *)(content + 2 * sizeof(int));
					off_t offset = *(off_t *)(content + 2 * sizeof(int) + sizeof(size_t));
					char *tembuf = malloc(limit);
					// call orig getdirentries function
					ssize_t re = getdirentries(fd, tembuf, limit, &offset);
					// form the return message
					int message_size = 2 * sizeof(int) + sizeof(ssize_t) + re;
					// if what we get is smaller than 0, return size should be 0
					if (re < 0) message_size -= re;
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &re, sizeof(ssize_t));
					if (re > 0) memcpy(msg + 2 * sizeof(int) + sizeof(ssize_t), tembuf, re);
					// send return message
					send(sessfd, msg, message_size, 0);
					free(tembuf);
					free(msg);
				} else if (type == 8) { // read
					// copy info from the message received
					int fd = *(int *)(content + sizeof(int));
					size_t limit = *(size_t *)(content + 2 * sizeof(int));
					char *tembuf = malloc(limit);
					// call orig read function
					ssize_t re = read(fd, tembuf, limit);
					// form return msg
					int message_size = 2 * sizeof(int) + sizeof(ssize_t) + re + 1;
					if (re < 0) message_size -= re;
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &re, sizeof(ssize_t));

					if (re > 0) {
						memcpy(msg + 2 * sizeof(int) + sizeof(ssize_t), tembuf, re);
						msg[message_size] = '\n';
					}
					send(sessfd, msg, message_size, 0);
					free(tembuf);
					free(msg);
				} else if (type == 9) { // getdirtree
					// copy info from the message received
					char *marshalled_tree;
					int string_length = size - 2 * sizeof(int);
					char *path = malloc(string_length + 1);
					memcpy(path, content + sizeof(int), string_length);
					path[string_length] = 0;
					// call orig getdirtree function
					struct dirtreenode *root = getdirtree(path);
					// the length return message will be
					int length = 0;
					// the number of nodes of the tree
					int node_number = 0;
					// if the root is not null, marshall the tree
					if (root != NULL) marshalled_tree = tree_marshall(root, &length, &node_number);
					// form the return message
					int message_size = 4 * sizeof(int) + length;
					char *msg = malloc(message_size);
					memcpy(msg, &message_size, sizeof(int));
					memcpy(msg + sizeof(int), &errno, sizeof(int));
					memcpy(msg + 2 * sizeof(int), &length, sizeof(int));
					memcpy(msg + 3 * sizeof(int), &node_number, sizeof(int));
					memcpy(msg + 4 * sizeof(int), marshalled_tree, length);
					send(sessfd, msg, message_size, 0);
					free(path);
					if(root != NULL) free(marshalled_tree);
					free(msg);
					freedirtree(root);
				} else if (type == 10) {
					// this is called when client is closed and server should kill the process
					free(content);
					close(sessfd);
					exit(0);
				}
				free(content);
			}
			if (rv<0) err(1,0);
		}
		close(sessfd);
	}
	// close socket
	close(sockfd);

	return 0;
}

// this function is used to marshall the tree
char *tree_marshall(struct dirtreenode *root, int *length, int *node_number) {
	// first calculate the number of nodes and the length of the message
	calculate(root, node_number, length);

	char *marshalled = malloc(*length);
	int offset = 0;
	// this is a Queue of node pointer used to iterate the tree
	char **queue = malloc(*node_number * PSIZE);
	int start = 0;
	int queue_size = 0;

	// root enqueue
	memcpy(queue + start + queue_size, &root, PSIZE);
	queue_size++;
	
	while (queue_size != 0) {
		int n = queue_size;
		int i;
		for (i = 0; i < n; i++) {
			// dequeue a node
			struct dirtreenode *tem = (struct dirtreenode *)(*(queue + start));
			start++;
			queue_size--;

			// serilized the msg of this node
			memcpy(marshalled + offset, tem->name, strlen(tem->name));
			offset += strlen(tem->name) + 1;
			// add 0 at the end of the name string
			*(marshalled + offset - 1) = 0;
			memcpy(marshalled + offset, &tem->num_subdirs, sizeof(int));
			offset += sizeof(int);

			// enqueue the son nodes
			int j;
			for (j = 0; j < tem->num_subdirs; j++) {
				memcpy(queue + start + queue_size, tem->subdirs + j, PSIZE);
				queue_size++;
			}
		}
	}
	free(queue);
	return marshalled;
}

// this function is used to calculate the number of the nodes and the size of whole msg
void calculate(struct dirtreenode *root, int *number, int *size) {
	*number = *number + 1;
	*size = *size + strlen(root->name) + 1 + sizeof(int);
	if (root->num_subdirs == 0) return;
	int i;
	for (i = 0; i < root->num_subdirs; i++) {
		calculate(*(root->subdirs + i), number, size);
	}
	return;
}