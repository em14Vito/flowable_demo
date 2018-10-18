# 1. Start process
http://localhost:8080/start
{
	"processId":"ThreeLevelAuditing",
	"money":10002
}

#2. Query Task by processDefinition
http://localhost:8080/process/candidate/ThreeLevelAuditing


Other API:
# Query active task by processID
http://localhost:8080/task/candidate?processId=2501

# Claim Task
http://localhost:8080/task/accept
{
​	"taskId":"2509",
​	"userId":"cathay"
}


# Complete Task
http://localhost:8080/task/complete
{
​	"taskId":"2509"
}


# Query History
http://localhost:8080/process/history/2501
