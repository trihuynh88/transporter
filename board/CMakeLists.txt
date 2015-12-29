cmake_minimum_required(VERSION 2.8)
project(Transporter_Central_Server)
find_package( OpenCV REQUIRED )
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES transporter_noaudio_timer_webcams_queue.cpp)
add_executable(Transporter_Board ${SOURCE_FILES})
target_link_libraries( Transporter_Board ${OpenCV_LIBS} )