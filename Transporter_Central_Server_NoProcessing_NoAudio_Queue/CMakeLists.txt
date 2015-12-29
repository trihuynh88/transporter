cmake_minimum_required(VERSION 3.2)
project(Transporter_Central_Server)
find_package( OpenCV REQUIRED )
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES main.cpp)
add_executable(Transporter_Central_Server ${SOURCE_FILES})
target_link_libraries( Transporter_Central_Server ${OpenCV_LIBS} )
