# Environnement de dev local (Git Bash). Surchargez JAVA_HOME / NODE_HOME / MAVEN_HOME si besoin.
export JAVA_HOME="${JAVA_HOME_21:-$HOME/tools/jdk-21.0.12.1+1}"
export NODE_HOME="${NODE_HOME:-$HOME/tools/node-v22.23.4-win-x64}"
export PATH="$JAVA_HOME/bin:$NODE_HOME:$PATH"
