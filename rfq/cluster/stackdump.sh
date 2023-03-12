#!/bin/sh
pid=$(ps -ef | grep cluster | grep -v grep | awk '{print $2}')
jstack $pid
