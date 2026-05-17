pipeline {
    agent any

    tools {
        maven 'Maven 3' 
        jdk 'JDK 17'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Spring Backend') {
            steps {
                dir('backend-spring') {
                    sh 'mvn clean install'
                }
            }
        }
        
        stage('Build Frontend') {
            steps {
                sh 'npm install'
                sh 'npm run build'
            }
        }

        stage('Docker Build and Push') {
            steps {
                script {
                    // Docker setup logic goes here
                    echo 'Building Docker images...'
                    sh 'docker-compose build'
                }
            }
        }
    }
}
