pipeline {

    agent { label "master" }

    /*parameters {       
        string(name: 'ACCOUNTS', defaultValue: 'dev-raghu,dev-raghu2', description: 'Comma separated list of accounts to deploy to')
    }*/

    environment {
        // Parent folder and deploy env will often be the same
        PARENT_FOLDER           = 'dev'
        DEPLOY_ENVIRONMENT      = 'dev'
        IS_JENKINS_MODE         = "true"
        GIT_REPO                = "https://github.com/RaghuPrakhya/tests3.git"
        GIT_CREDENTIALS         = "RaghuPrakhya"
        // Recipient of the notification emails
        EMAIL_RECIPIENT = 'RaghuPrakhya@gmail.com' // Multiple emails can be separated by a semi-colon
        ACCOUNTS = 'dev-raghu,dev-raghu2'
    }

    stages {
        stage("Initialization") {
            steps {
                //Cleanup Workspace
                cleanWs()
                git branch: "master", changelog: false, credentialsId: "${GIT_CREDENTIALS}", poll: false, url: "${GIT_REPO}"
                echo "Initialization stage"

            }
            // post section to trigger email on failure
            post {
                failure {
                    script {
                        echo "Initialization failure stage"
                        currentBuild.result = 'FAILURE'
                        notifyBuild(currentBuild.result)
                    }
                }
            }
        }



          stage("Execute on all accounts") {
              steps {
                  sh '''
                      echo "Path is $PATH"
                      cat $0
                      echo "Accounts are $ACCOUNTS"
                      OIFS=$IFS;
                      IFS=",";
                      
                      ACCOUNTS_ARRAY=($ACCOUNTS);
                      WORKSPACE=$(pwd);
  
                      cd ${WORKSPACE}

                      ls -ltR

                      ## Loop through all the accounts provided
                      for ((i=0; i<${#ACCOUNTS_ARRAY[@]}; ++i)); do     
                     

                          ## For each account determine the account number
                          if [ ${ACCOUNTS_ARRAY[$i]} == 'dev-raghu' ]; then
                              ACCOUNT_NUM='770765425423'
                          elif [ ${ACCOUNTS_ARRAY[$i]} == 'dev-raghu2' ]; then
                              ACCOUNT_NUM='770765425423'
                          fi
  
                          echo "Iteration : $i"
                          echo "${ACCOUNTS_ARRAY[$i]} : $ACCOUNT_NUM"
  
                          ## Assume a role into the dev account
                          STSRESPONSE=$(aws sts assume-role --role-arn arn:aws:iam::${ACCOUNT_NUM}:role/cloudformation-role --role-session-name jenkins)
                          AWS_ACCESS_KEY_ID=$(echo "${STSRESPONSE}" | jq '.Credentials.AccessKeyId' | cut -d'"' -f2)
                          AWS_SECRET_ACCESS_KEY=$(echo "${STSRESPONSE}" | jq '.Credentials.SecretAccessKey' | cut -d'"' -f2)
                          AWS_SESSION_TOKEN=$(echo "${STSRESPONSE}" | jq '.Credentials.SessionToken' | cut -d'"' -f2)
                          export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
                          export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
                          export AWS_SESSION_TOKEN=${AWS_SESSION_TOKEN}

                          ######################################
                          #### Take deployment actions here ####
                          ######################################
                          
                          #export DEPLOY_ENVIRONMENT=${ACCOUNTS_ARRAY[$i]}
                          #aws s3 ls
  
                          runway plan

                          ################################
                          #### End deployment actions ####
                          #################################
  
                          ## Assume role back to prod. Prod is the only account that can assume roles
                          STSRESPONSE=$(curl http://169.254.169.254/latest/meta-data/iam/security-credentials/JenkinsRole)
                          AWS_ACCESS_KEY_ID=$(echo "${STSRESPONSE}" | jq '.AccessKeyId' | cut -d'"' -f2 )
                          AWS_SECRET_ACCESS_KEY=$(echo "${STSRESPONSE}" | jq '.SecretAccessKey' | cut -d'"' -f2)
                          AWS_SESSION_TOKEN=$(echo "${STSRESPONSE}" | jq '.Token' | cut -d'"' -f2)
                          export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
                          export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
                          export AWS_SESSION_TOKEN=${AWS_SESSION_TOKEN}
                      done
  
                      ## Reset IFS variable at the end
                      IFS=$OIFS;
                  '''
              }
          }

          stage('Send email') {
            steps {
                script {
                    // Set build result and trigger email for successful build
                    currentBuild.result = 'SUCCESS'
                    notifyBuild(currentBuild.result)
                }
            }
        }         
    }

}

// Function to  send notification email
def notifyBuild(String buildStatus = 'STARTED') {
    echo buildStatus
}
