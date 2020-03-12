pipeline{

    agent { label "master" }

    environment {
        // Parent folder and deploy env will often be the same
        PARENT_FOLDER           = 'dev'
        DEPLOY_ENVIRONMENT      = 'dev'        
        IS_JENKINS_MODE         = "true"
        CI                      = "true"
        GIT_REPO                = "https://github.com/RaghuPrakhya/tests3.git"
        GIT_CREDENTIALS         = "RaghuPrakhya@gmail.com"
        // Recipient of the notification emails
        EMAIL_RECIPIENT = 'RaghuPrakhya@gmail.com' // Multiple emails can be separated by a semi-colon
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
                        currentBuild.result = 'FAILURE'
                        notifyBuild(currentBuild.result)
                        echo "Initialization failure stage"
                    }
                }
            }
        }



        /*
            Because the Jenkins server exists in prod we must assume a role into the environment that we want to interact with.
            This will cause all commands we run after this to be executed against that environment instead of prod.
            After assuming a role if you need to execute commands against prod again you must assume your role back into production.
            If after assumeing a role you need to assume a role into another account/environment you must assume the role back into production first since production is the only one allowed to assume roles into other accounts.
            If you needed to assume a role in dev and then test it would look something like this:
            - Assume role in dev
            - Execute dev commands
            - Assume role in prod
            - Assume role in test
            - Execute test commands
        */
//        stage('Assume Role') {
//            steps {
//                // Set AWS Credentials
//                script {
//                    env.STSRESPONSE=sh(returnStdout: true, script: "aws sts assume-role --role-arn arn:aws:iam::178375044839:role/baxaws-sandbox-operator-admin --role-session-name jenkins")
//                    env.AWS_ACCESS_KEY_ID = sh(returnStdout: true, script: "echo \$STSRESPONSE | jq -r .Credentials.AccessKeyId").trim()
//                    env.AWS_SECRET_ACCESS_KEY = sh (returnStdout: true, script: "echo \$STSRESPONSE | jq -r .Credentials.SecretAccessKey").trim()
//                    env.AWS_SESSION_TOKEN = sh (returnStdout: true, script: "echo \$STSRESPONSE | jq -r .Credentials.SessionToken").trim()
//                    echo "Assume stage"
//                }
//            }
//            // post section to trigger email on failure
//            post {
//                failure {
//                    script {
//                        currentBuild.result = 'FAILURE'
//                        notifyBuild(currentBuild.result)
//                    }
//                }
//            }
//        }

        /*
            https://github.com/onicagroup/runway/
            Runway is a wrapper for cloudformation.
            This wrapper allows us to use this standard directory structure to deploy infrastructure.
            It also allows use to use environment files to extract away the differences between environments and have a standard deployment to every environment
            Runway has a few commands:
            - runway test (aka runway preflight) - execute this in your environment to catch errors; if it exits 0, you're ready for...
            - runway plan (aka runway taxi) - this optional step will show the diff/plan of what will be changed. With a satisfactory plan you can...
            - runway deploy (aka runway takeoff) - if running interactively, you can choose which deployment to run; otherwise (i.e. on your CI system) each deployment will be run in sequence.
        */
        stage ('Preflight Check') {
            steps {
               
                sh 'cd deployments/${PARENT_FOLDER} && runway plan 2>&1 | tee plan.txt; chngCnt=`grep \'full changeset\' plan.txt | wc -l `; echo "Add count is $chngCnt"; if [[ $chngCnt -gt 0 ]]; then exit 0; else echo exit 1; fi'
            }
            // post section to trigger email on failure
            post {
                failure {
                    script {
                        currentBuild.result = 'FAILURE'
                        notifyBuild(currentBuild.result)
                    }
                }
            }
        }

        /*
            https://github.com/onicagroup/runway/
            Runway is a wrapper for cloudformation.
            This wrapper allows us to use this standard directory structure to deploy infrastructure.
            It also allows use to use environment files to extract away the differences between environments and have a standard deployment to every environment
            Runway has a few commands:
            - runway test (aka runway preflight) - execute this in your environment to catch errors; if it exits 0, you're ready for...
            - runway plan (aka runway taxi) - this optional step will show the diff/plan of what will be changed. With a satisfactory plan you can...
            - runway deploy (aka runway takeoff) - if running interactively, you can choose which deployment to run; otherwise (i.e. on your CI system) each deployment will be run in sequence.
        */
        stage ('Deploy') {
            steps {
                //sh "cd deployments/${PARENT_FOLDER} && runway deploy"
                sh "cd deployments/${PARENT_FOLDER}"
		echo "cd deployments/${PARENT_FOLDER}"
            }
            // post section to trigger email on failure
            post {
                failure {
                    script {
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
    buildStatus =  buildStatus ?: 'SUCCESSFUL'
    emailext (
        to: env.EMAIL_RECIPIENT,
        from: 'no-reply@baxter.com',
        subject: "Jenkins: '${env.JOB_NAME} [#${env.BUILD_NUMBER}] - $buildStatus'",
        body: """
        Jenkins Job ${env.JOB_NAME} [#${env.BUILD_NUMBER}] - $buildStatus
        Check console output at ${env.BUILD_URL}
        """
    )
}
