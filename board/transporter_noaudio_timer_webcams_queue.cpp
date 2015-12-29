#include <opencv2/opencv.hpp>
#include <iostream>
#include <cassert>
#include <cmath>
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
/*
#include <linux/soundcard.h>
#include <alsa/asoundlib.h>
#include <alsa/pcm.h>
*/
#include <sys/time.h>

using namespace cv;
using namespace std;

struct sockaddr_in serv_addr, cli_addr;
unsigned int clilen;
bool isConnected = false;
int newsockfd;
int fps = 12;
int TimeWait = 1000000/fps;
struct timeval tv;
int queueLen = 200;
char queueImageCaptured[200][300000];
int queueImageCapturedLen[200];
int queueImageCapturedFront = 0, queueImageCapturedRear = 0;
int numCaptured = 0, numSent = 0;

void setTimer(struct timeval &tv)
{
	gettimeofday(&tv,NULL);
}

int checkTimer(struct timeval &tv, suseconds_t usec)
{
	struct timeval ctv;
	gettimeofday(&ctv,NULL);
	if (abs(ctv.tv_usec-tv.tv_usec)>=usec)
	{
		gettimeofday(&tv,NULL);
		return 1;
	}
	else
		return 0;
}

void* readingMessage(void* sockin)
{
    int sock = *(int*)sockin;
    int read_size;
    char message_in[4];
    int numWrite;

    while ((read_size = read(sock,message_in,sizeof(message_in)))>0)
    {
        if (message_in[0]=='Q' && message_in[1]=='U' && message_in[2]=='I' && message_in[3]=='T')
        {
            isConnected = false;
            close(newsockfd);
            cout<<"Client closed connection.\n";
            break;
        }
    }
}

void* writingVideo(void* sockin)
{
    int sock = *(int*)sockin;
    int numWrite;
    char messageSize[8];
    while (isConnected)
    {
	if (numCaptured>numSent)
	{
	    sprintf(messageSize, "%08d", queueImageCapturedLen[queueImageCapturedFront]);
	    numWrite = write(sock, messageSize , 8);
	    numWrite = write(sock, queueImageCaptured[queueImageCapturedFront], queueImageCapturedLen[queueImageCapturedFront]);		
	    queueImageCapturedFront = (queueImageCapturedFront+1)%queueLen;
	    numSent++;
	}
    }
}

const int HORIZONTAL_CROP = 60;

int main() {

    bool isLocalMode = false;
    //Server Socket

    int sockfd, portno;

    char buffer[256];
    int  n;

	
    setTimer(tv);
    //-----Connection

    sockfd = socket(AF_INET, SOCK_STREAM, 0);

    if (sockfd < 0)
    {
        perror("ERROR opening socket");
        exit(1);
    }

    bzero((char *) &serv_addr, sizeof(serv_addr));
    portno = 8888;

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = INADDR_ANY;
    serv_addr.sin_port = htons(portno);

    const int      optVal = 1;
    const socklen_t optLen = sizeof(optVal);

    int rtn = setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, (char*) &optVal, optLen);

    assert(rtn == 0);

    if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0)
    {
        perror("ERROR on binding");
        exit(1);
    }

    listen(sockfd,5);
    clilen = sizeof(cli_addr);

    //----------


    VideoCapture cap(0);
    VideoCapture cap2(1);
	//VideoCapture cap("/home/firefly/Downloads/shaky_streetmusic.mp4");
	//VideoCapture cap2("/home/firefly/Downloads/shaky_streetmusic.mp4");
    //cap.set(CV_CAP_PROP_FOURCC ,CV_FOURCC('M', 'J', 'P', 'G') );
    //cap.set(CV_CAP_PROP_FRAME_WIDTH, 1280);
    //cap.set(CV_CAP_PROP_FRAME_HEIGHT, 720);
    //cap.set(CV_CAP_PROP_FRAME_WIDTH, 320);
    //cap.set(CV_CAP_PROP_FRAME_HEIGHT, 240);
    //cap.set(CV_CAP_PROP_FPS,15);

    //cap2.set(CV_CAP_PROP_FOURCC ,CV_FOURCC('M', 'J', 'P', 'G') );
    //cap2.set(CV_CAP_PROP_FRAME_WIDTH, 1280);
    //cap2.set(CV_CAP_PROP_FRAME_HEIGHT, 720);
    //cap2.set(CV_CAP_PROP_FRAME_WIDTH, 320);
    //cap2.set(CV_CAP_PROP_FRAME_HEIGHT, 240);
    //cap2.set(CV_CAP_PROP_FPS,15);

    assert(cap.isOpened());
    assert(cap2.isOpened());

    Mat curImage, preImage;
    Mat curImage2, preImage2;

    cap >> preImage;
    cap2 >> preImage2;

    //int shift = 18;
    //Rect myROI(preImage.cols/6,shift,preImage.cols*4/6,preImage.rows-shift);
    //Rect myROI2(preImage.cols/6,0,preImage.cols*4/6,preImage.rows-shift);
    //preImage = preImage(myROI);
    //preImage2 = preImage2(myROI2);

    int sizeRowOrig = preImage.rows;
    int sizeColOrig = preImage.cols;
    int vertical_crop = HORIZONTAL_CROP*sizeRowOrig/sizeColOrig;


    double ResizeFactor = 2.0/3.0;
    //resize(preImage,preImage,Size(sizeColOrig*ResizeFactor+2*HORIZONTAL_CROP,sizeRowOrig*ResizeFactor+2*vertical_crop));
    //resize(preImage2,preImage2,Size(sizeColOrig*ResizeFactor+2*HORIZONTAL_CROP,sizeRowOrig*ResizeFactor+2*vertical_crop));

    cout<<preImage.rows<<" "<<preImage.cols<<endl;

    int imgWidth=640, imgHeight=480;
    char messageSize[8];
    vector<int> paramCompress = vector<int>(2);
    paramCompress[0]=CV_IMWRITE_JPEG_QUALITY;
    paramCompress[1]=90;
    int* pSockMessage = (int*)malloc(1);

    while (true)
    {
        if (!isConnected && !isLocalMode)
        {
	    queueImageCapturedFront = 0;
	    queueImageCapturedRear = 0;
	    numCaptured = 0;
	    numSent = 0;

            cout<<"Waiting for client to connect..."<<endl;
            newsockfd = accept(sockfd, (struct sockaddr *)&cli_addr, &clilen);
            if (newsockfd < 0)
            {
                perror("ERROR on accept");
                exit(1);
            }

            isConnected = true;

            pthread_t threadReadClientMessage;
            *pSockMessage = newsockfd;
            if( pthread_create( &threadReadClientMessage , NULL ,  readingMessage , (void*) pSockMessage) < 0)
            {
                perror("could not create thread");
                return 1;
            }

	    pthread_t threadWriteVideo;
            if( pthread_create( &threadWriteVideo , NULL ,  writingVideo , (void*) pSockMessage) < 0)
            {
                perror("could not create thread");
                return 1;
            }

            sprintf(messageSize,"%08d",imgWidth);
            //------Connection--------

            n = write(newsockfd,messageSize,8);

            if (n < 0)
            {
                perror("ERROR writing to socket");
                exit(1);
            }

            sprintf(messageSize,"%08d",imgHeight);

            n = write(newsockfd,messageSize,8);

        }
        namedWindow("StereoCam",WINDOW_AUTOSIZE);
	if (checkTimer(tv,TimeWait)==1)
	{
		cout<<"inside"<<endl;
		cap >> curImage;
		cap2 >> curImage2;

		//curImage = curImage(myROI);
		//curImage2 = curImage2(myROI2);	

		//resize(curImage,curImage,Size(sizeColOrig*ResizeFactor+2*HORIZONTAL_CROP,sizeRowOrig*ResizeFactor+2*vertical_crop));
		//resize(curImage2,curImage2,Size(sizeColOrig*ResizeFactor+2*HORIZONTAL_CROP,sizeRowOrig*ResizeFactor+2*vertical_crop));

		Mat canvas = Mat::zeros(preImage.rows,preImage.cols*2+10,preImage.type());
		Mat imgStereo = Mat::zeros(preImage.rows,preImage.cols*2,preImage.type());
		vector<uchar> imgCompressed;


		curImage.copyTo(imgStereo(Range(0,preImage.rows),Range(0,preImage.cols)));
		curImage2.copyTo(imgStereo(Range(0,preImage.rows),Range(preImage.cols,preImage.cols*2)));

		imencode(".jpg",imgStereo,imgCompressed,paramCompress);

		curImage.copyTo(canvas(Range(0,preImage.rows),Range(0,preImage.cols)));
		curImage2.copyTo(canvas(Range(0,preImage.rows),Range(preImage.cols+10, preImage.cols*2+10)));

		imshow("StereoCam",canvas);

		memcpy(queueImageCaptured[queueImageCapturedRear],&imgCompressed[0],imgCompressed.size());
		queueImageCapturedLen[queueImageCapturedRear] = imgCompressed.size();
		queueImageCapturedRear = (queueImageCapturedRear+1)%queueLen;
		numCaptured++;
	}
	else
		cout<<"-------outside"<<endl;
        //-------------
        char keyPressed;
        keyPressed = waitKey(1);
        if (keyPressed=='q') {
            break;
        }
    }
    cap.release();
    cap2.release();
    close(newsockfd);
    close(sockfd);
    return 0;
}
