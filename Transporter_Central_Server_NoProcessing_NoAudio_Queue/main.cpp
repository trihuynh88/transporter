#include <opencv2/opencv.hpp>
#include <iostream>
#include <cassert>
#include <fstream>

#include <stdio.h>
#include <stdlib.h>
#include <netdb.h>
#include <netinet/in.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <sys/ioctl.h>

#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <stdlib.h>
#include <stdio.h>


using namespace std;
using namespace cv;

const int HORIZONTAL_CROP = 60;
struct sockaddr_in addrServerVideoIn, addrServerVideoOut;
struct sockaddr_in addrClientVideoIn, addrClientVideoOut;
unsigned int lenClientVideoIn, lenClientVideoOut;
bool isConnected = false;

int sockVideoIn, sockVideoOut;
int sockVideoInPort = 8888, sockVideoOutPort = 2222;
int sockVideoInConnection, sockVideoOutConnection;
Mat rawImage;
char message_image[1000000];
int lenImage;
vector<char> dataImage;
bool hasRawImage = false;
bool flagWriteImage = false;
bool flagReadImage = false;
bool flagReadImageStereo = false;
bool flagWriteImageStereo = false;
Mat imgStereo;
bool hasImageStereo = false;
pthread_t threadReadVideo;
pthread_t threadWriteVideo;
pthread_t threadReadClientMessage;
pthread_t threadStabilizeVideo;

int lenQueue = 500;
vector<char> queueImageReceived[500];
Mat queueImageProcessed[500];
int frontImageReceived=0, rearImageReceived=lenQueue-1;
int numImageReceived = 0, numImageSent = 0;

void* readingVideo(void* sockin)
{
    int numRead;
    char message_in[1000000];
    int numWrite;
    int lenMessage;
    int hasError = 0;

    numRead = read(sockVideoInConnection,message_in,8);
    numWrite = write(sockVideoOutConnection,message_in,8);
    numRead = read(sockVideoInConnection,message_in,8);
    numWrite = write(sockVideoOutConnection,message_in,8);
    while (isConnected) {
        memset(message_in,0,sizeof(message_in));
        numRead = read(sockVideoInConnection,message_in,8);
        lenMessage = atoi(message_in);
        lenImage = lenMessage;
        int start = 0;
        while (lenMessage>0) {
            numRead = read(sockVideoInConnection,&message_image[start],lenMessage);
            if (numRead>0) {
                start+=numRead;
                lenMessage-=numRead;
            }
        }
        rearImageReceived = (rearImageReceived + 1) % lenQueue;

        queueImageReceived[rearImageReceived].resize(lenImage);
        memcpy(&queueImageReceived[rearImageReceived][0], message_image, lenImage);
        numImageReceived++;

    }
}

void* writingVideo(void* sockin)
{
    int numWrite;
    char messageSize[8];
    while (isConnected)
    {
        if (numImageSent<numImageReceived) {

            sprintf(messageSize, "%08d", queueImageReceived[frontImageReceived].size());
            numWrite = write(sockVideoOutConnection, messageSize, 8);
            numWrite = write(sockVideoOutConnection, &queueImageReceived[frontImageReceived][0],
                             queueImageReceived[frontImageReceived].size());
            frontImageReceived = (frontImageReceived + 1) % lenQueue;
            numImageSent++;
        }
    }
}

void* readingMessage(void* sockin)
{
    int numRead;
    char message_in[4];

    while ((numRead = read(sockVideoOutConnection,message_in,sizeof(message_in)))>0)
    {
        write(sockVideoInConnection,message_in,sizeof(message_in));
        if (message_in[0]=='Q' && message_in[1]=='U' && message_in[2]=='I' && message_in[3]=='T')
        {
            isConnected = false;

            shutdown(sockVideoInConnection,SHUT_RDWR);
            shutdown(sockVideoOutConnection,SHUT_RDWR);
            close(sockVideoInConnection);
            close(sockVideoOutConnection);

            pthread_cancel(threadWriteVideo);
            pthread_cancel(threadReadVideo);

            break;
        }
    }
}


int main() {

    //Server Socket

    const int      optVal = 1;
    const socklen_t optLen = sizeof(optVal);

    int  n;

    //-----Connection

    sockVideoIn = socket(AF_INET, SOCK_STREAM, 0);

    if (sockVideoIn < 0)
    {
        perror("ERROR opening socket");
        exit(1);
    }

    bzero((char *) &addrServerVideoIn, sizeof(addrServerVideoIn));

    addrServerVideoIn.sin_family = AF_INET;
    addrServerVideoIn.sin_addr.s_addr = INADDR_ANY;
    addrServerVideoIn.sin_port = htons(sockVideoInPort);

    int rtn = setsockopt(sockVideoIn, SOL_SOCKET, SO_REUSEADDR, (char*) &optVal, optLen);

    assert(rtn == 0);

    if (bind(sockVideoIn, (struct sockaddr *) &addrServerVideoIn, sizeof(addrServerVideoIn)) < 0)
    {
        perror("ERROR on binding");
        exit(1);
    }

    listen(sockVideoIn,5);

    //----SocketServerOut

    sockVideoOut = socket(AF_INET, SOCK_STREAM, 0);

    if (sockVideoOut < 0)
    {
        perror("ERROR opening socket");
        exit(1);
    }

    bzero((char *) &addrServerVideoOut, sizeof(addrServerVideoOut));

    addrServerVideoOut.sin_family = AF_INET;
    addrServerVideoOut.sin_addr.s_addr = INADDR_ANY;
    addrServerVideoOut.sin_port = htons(sockVideoOutPort);

    rtn = setsockopt(sockVideoOut, SOL_SOCKET, SO_REUSEADDR, (char*) &optVal, optLen);

    assert(rtn == 0);

    if (bind(sockVideoOut, (struct sockaddr *) &addrServerVideoOut, sizeof(addrServerVideoOut)) < 0)
    {
        perror("ERROR on binding");
        exit(1);
    }

    listen(sockVideoOut,5);

    //----------
    while (true)
    {
        if (!isConnected)
        {
            printf("Waiting for Client to connect...\n");
            fflush(stdout);
            sockVideoInConnection = accept(sockVideoIn, (struct sockaddr *)&addrClientVideoIn, &lenClientVideoIn);
            printf("VideoIn Connected\n");
            fflush(stdout);
            sockVideoOutConnection = accept(sockVideoOut, (struct sockaddr *)&addrClientVideoOut, &lenClientVideoOut);
            printf("VideoOut Connected\n");
            fflush(stdout);
            frontImageReceived=0;

            rearImageReceived=lenQueue-1;

            numImageReceived = 0;

            numImageSent = 0;


            isConnected = true;

            char buf[4];
            buf[0]='G';
            buf[1]='O';
            write(sockVideoInConnection,buf,sizeof(buf));

            if (sockVideoInConnection < 0)
            {
                perror("ERROR on accept");
                exit(1);
            }
            if (sockVideoOutConnection < 0)
            {
                perror("ERROR on accept");
                exit(1);
            }

            if( pthread_create( &threadReadVideo , NULL ,  readingVideo , NULL) < 0)
            {
                perror("could not create thread");
                return 1;
            }


            if( pthread_create( &threadWriteVideo , NULL ,  writingVideo , NULL) < 0)
            {
                perror("could not create thread");
                return 1;
            }


            if( pthread_create( &threadReadClientMessage , NULL ,  readingMessage , NULL) < 0)
            {
                perror("could not create thread");
                return 1;
            }
        }
    }

    close(sockVideoIn);
    close(sockVideoOut);
    close(sockVideoInConnection);
    close(sockVideoOutConnection);
    return 0;
}