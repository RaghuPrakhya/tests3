pipeline {

    agent { label "master" }

    /*parameters {
        string(name: 'ACCOUNTS', defaultValue: 'dev-raghu,dev-raghu2', description: 'Comma separated list of accounts to deploy to')
    }*/

    environment {
        // Parent folder and deploy env will often be the same
        PARENT_FOLDER           = 'dev'
        //DEPLOY_ENVIRONMENT      = 'dev'
        IS_JENKINS_MODE         = "true"
        GIT_REPO                = "https://github.com/RaghuPrakhya/tests3.git"
        GIT_CREDENTIALS         = "RaghuPrakhya"
        // Recipient of the notification emails
        EMAIL_RECIPIENT = 'RaghuPrakhya@gmail.com' // Multiple emails can be separated by a semi-colon
        ACCOUNTS = 'dev-raghu,dev-raghu2'
        REGIONS = 'us-east-2,ap-southeast-1,eu-central-1'
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

        stage("Deploy on all accounts") {
            steps {
                sh '''
                    #echo "Path is $PATH"
                    #which sh
                    cp $0 /tmp/rtrash
                    echo "Accounts are $ACCOUNTS"
                    OIFS=$IFS;
                    IFS=",";

                    ACCOUNTS_ARRAY=($ACCOUNTS);
                    REGIONS_ARRAY=($REGIONS);
                    
                    WORKSPACE=$(pwd);
                    cd ${WORKSPACE}

                    ## Loop through all the accounts provided
                    for ((i=0; i<${#ACCOUNTS_ARRAY[@]}; ++i)); do
                        
                        echo "Iteration : $i"
                        sacct=${ACCOUNTS_ARRAY[$i]}
                        
                        bkt=rptestaccess
                        codebkt=rplambda
                        for f in `find . -name *.env | tr '\n' ','`
                        do
                         echo "Substituting ${sacct} in place of existing short account in $f"
                         echo Contents of file before change
                         cat $f
                         sed -i -e "s/bkt:.*$/bkt: ${bkt}/" $f
                         echo Contents of file after change
                         cat $f
                        done
                                                
                        ## For each account determine the account number
                        if [ ${sacct} == 'dev-raghu' ]; then
                            ACCOUNT_NUM='770765425423'
                        elif [ ${sacct} == 'dev-raghu2' ]; then
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
                        
                        ################################################################################
                        #### Copying Lambda code to S3 and setting the code bucket in the env files ####
                        ################################################################################
                        
                        for ((i=0; i<${#REGIONS_ARRAY[@]}; ++i)); do
                         rgn=${REGIONS_ARRAY[$i]}
                         s3bkt="${codebkt}-${rgn}"
                         s3url="s3://${codebkt}-${rgn}"
                         for f in `find . -name *.zip | tr '\n' ','`
                         do
                          echo "Copying $f to the bucket ${s3bkt}"
                          aws s3 cp $f ${s3url}
                          echo "Copyied $f to the bucket ${s3bkt}"
                         done
                         echo "Setting the code bucket in envirnment file to ${S3bkt}"
                         for f in `find . -name *${rgn}.env | tr '\n' ','`
                         do
                          echo "Substituting ${s3bkt} in place of existing the current bucket name"
                          echo Contents of file before change
                          cat $f
                          sed -i -e "s/s3bkt:.*$/s3bkt: ${s3bkt}/" $f
                          echo Contents of file after change
                          cat $f
                         done
                        done
                        ######################################
                        #### Take deployment actions here ####
                        ######################################

                        #export DEPLOY_ENVIRONMENT=${ACCOUNTS_ARRAY[$i]}
                        #aws s3 ls

                        /runway/runway plan 2>&1 | tee /tmp/plan.txt

                        chngCnt=`grep "INFO:runway.cfngin.providers.aws.default:.* full changeset" /tmp/plan.txt | wc -l `
                        noChngCnt=`grep "INFO:runway.cfngin.actions.diff:No changes" /tmp/plan.txt | wc -l`

                        echo "Add count is $chngCnt"
                        echo "Change count is $noChngCnt"

                        if [ ${chngCnt} -gt 0 -a ${noChngCnt} -eq 0 ];
                        then
                          /runway/runway deploy
                        elif [ ${chngCnt} -eq 0 -a ${noChngCnt} -gt 0 ];
                        then
                          export CI=1;
                          /runway/runway destroy;
                          unset CI;
                          /runway/runway deploy
                        else
                          echo "Please check the stacks manually. Some regions are depoyed and others not."
                        fi


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
            // post section to trigger email on failure
            post {
                failure {
                    script {
                        echo "Deployment failure stage"
                        currentBuild.result = 'FAILURE'
                        notifyBuild(currentBuild.result)
                    }
                }
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

