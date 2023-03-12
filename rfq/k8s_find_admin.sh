#!/bin/bash

line=$(kubectl describe pod aeron-io-sample-admin -n aeron-io-sample-admin | grep Name: | head -1)
IFS=" " read -ra fields <<< "$line"
admin="${fields[1]}"
echo $admin
