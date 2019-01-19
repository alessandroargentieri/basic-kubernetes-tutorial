
Usando solo i JAR copiati dalla cartella di progetto /target (dopo mvn clean package) e rinominati, senza usare docker o kubernetes dobbiamo andare nella cartella called:

> export CALLEDPORT=8086
> java -jar called.jar

Andiamo nella cartella caller:

> export CALLERPORT=8085
> export CALLEDADDRESS=http://localhost:8086
> java -jar caller.jar

Possiamo fare le chiamate http POST a http://localhost:8085/caller che chiamerà called e restituirà il valore JSON.

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Per usare docker e kubernetes dobbiamo costruire i due dockerfile che useranno i jar per comporre le immagini dei due servizi:
~~~~~~~~~
#Dockerfile called (called.jar in questo caso è nella stessa cartella del Dockerfile)
FROM openjdk:8-jdk-alpine
# ENV CALLEDPORT
ADD called.jar /
CMD ["java", "-jar", "called.jar"]
~~~~~~~~~~
poi costruiamo l'immagine andando NELLA CARTELLA DOVE E' PRESENTE IL DOCKERFILE da terminale:

> docker build -f Dockerfile -t alessandroargentieri/called-called .
> docker images

#PS: se volessi pushare le immagini create su DockerHub basta fare l'accesso:
> docker login -u="$USER_ID" -p="$USER_PWD"

#push dell'immagine su dockerhub:
> docker push $USER_ID/nomeimmagine

~~~~~~~~~~
#Dockerfile caller (caller.jar in questo caso è nella stessa cartella del Dockerfile)
FROM openjdk:8-jdk-alpine
#ENV CALLERPORT
#ENV CALLEDADDRESS
ADD caller.jar /
CMD ["java", "-jar", "caller.jar"]
~~~~~~~~~~
poi costruiamo l'immagine andando NELLA CARTELLA DOVE E' PRESENTE IL DOCKERFILE da terminale:

> docker build -f Dockerfile -t alessandroargentieri/caller-caller .
> docker images

#PS: se volessi pushare le immagini create su DockerHub basta fare l'accesso:
> docker login -u="$USER_ID" -p="$USER_PWD"

#push dell'immagine su dockerhub:
> docker push $USER_ID/nomeimmagine

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Ora che abbiamo le due immagini su DockerHub possiamo creare i file yaml da dare in pasto a kubernetes

Aggiorniamo minikube:
> minikube delete
> sudo rm -rf ~/.minikube
> curl -Lo minikube https://storage.googleapis.com/minikube/releases/v0.33.0/minikube-darwin-amd64 && chmod +x minikube && sudo cp minikube /usr/local/bin/ && rm minikube

> minikube start

> kubectl get namespace

useremo il namespace "default"

creiamo, dove vogliamo, i file yaml che prevedono 2 repliche di called, due repliche di caller e per gestire il load-balancing, ogni servizio avra' dei load balancer, rispettivamente caller-loadbalancer e called-loadbalancer.

~~~~~~~~~~~~~~~~~~~~~~~~
# deployment-called.yaml
~~~~~~~~~~~~~~~~~~~~~~~~
apiVersion: v1
kind: Service              
metadata:
  name: called-loadbalancer
spec:
  type: LoadBalancer       
  ports:
  - port: 8081               
    targetPort: 8081        
  selector:            
    app: called    
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: called
  labels:
    app: called
spec:
  replicas: 2                                             
  minReadySeconds: 15
  strategy:
    type: RollingUpdate                                   
    rollingUpdate: 
      maxUnavailable: 1                                   
      maxSurge: 1                                         
  selector:
    matchLabels:
      app: called
      tier: caller-called
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: called
        tier: caller-called
    spec:
      containers:
      - image: alessandroargentieri/called-called
        name: called
        env:
        - name: CALLEDPORT
          value: "8081"
        ports:
        - containerPort: 8081
          name: called
~~~~~~~~~~~~~~~~

~~~~~~~~~~~~~~~~~~~
#deployment-caller
~~~~~~~~~~~~~~~~~~~
apiVersion: v1
kind: Service              
metadata:
  name: caller-loadbalancer
spec:
  type: LoadBalancer       
  ports:
  - port: 8080               
    targetPort: 8080        
  selector:            
    app: caller    
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: caller
  labels:
    app: caller
spec:
  replicas: 2                                             
  minReadySeconds: 15
  strategy:
    type: RollingUpdate                                   
    rollingUpdate: 
      maxUnavailable: 1                                   
      maxSurge: 1                                         
  selector:
    matchLabels:
      app: caller
      tier: caller-called
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: caller
        tier: caller-called
    spec:
      containers:
      - image: alessandroargentieri/caller-caller
        name: caller
        env:
        - name: CALLERPORT
          value: "8080"
        - name: CALLEDADDRESS
          value: "http://called-loadbalancer.default.svc.cluster.local:8081" #"called-loadbalancer" #http://called called, called-loadbalancer
        ports:
        - containerPort: 8080
          name: caller
~~~~~~~~~~~~~~

Salvati i file non ci resta che avviare i deployment:

> kubectl delete deployments --all
> kubectl apply -f deployment-called.yaml
> kubectl apply -f deployment-caller.yaml

> kubectl get pods
> kubectl describe pod <pod-sha>
> kubectl get deployments
> kubectl get services
> minikube service list
|-------------|---------------------|-----------------------------|
|  NAMESPACE  |        NAME         |             URL             |
|-------------|---------------------|-----------------------------|
| default     | called-loadbalancer | http://192.168.99.100:30161 |
| default     | caller-loadbalancer | http://192.168.99.100:30078 |
| default     | kubernetes          | No node port                |
| kube-system | kube-dns            | No node port                |
|-------------|---------------------|-----------------------------|

vai da postman e fai una richiesta a caller tramite il suo load-balancer:

http://192.168.99.100:30078/caller POST {"title" : "Mr.", "name": "Alessandro", "surname": "Argentieri"}

(
  To access services inside Kubernetes you should use this DNS:
    http://caller-loadbalancer.default.svc.cluster.local:8080
    http://called-loadbalancer.default.svc.cluster.local:8081
)

minikube dashboard — url

IMPORTANTE: se i due load-balancer hanno porta (port) impostata ad 80 invece che ad 8080 ad esempio, allora la stringa di connessione sara' piu' semplice da specificare.
Possiamo inserire semplicemente:

value: "http://called-loadbalancer" 
invece che inserire tutta la stringa completa:
value: "http://called-loadbalancer.default.svc.cluster.local:80"

P.S. possiamo settare le varie porte (port) dei load balancer entrambe a 80, non ci saranno conflitti:

````````````````````````````````````````
apiVersion: v1
kind: Service              
metadata:
  name: called-loadbalancer
spec:
  type: LoadBalancer       
  ports:
  - port: 80               
    targetPort: 8081        
  selector:            
    app: called    
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: called
  labels:
    app: called
spec:
  replicas: 2                                             
  minReadySeconds: 15
  strategy:
    type: RollingUpdate                                   
    rollingUpdate: 
      maxUnavailable: 1                                   
      maxSurge: 1                                         
  selector:
    matchLabels:
      app: called
      tier: caller-called
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: called
        tier: caller-called
    spec:
      containers:
      - image: alessandroargentieri/called-called
        name: called
        env:
        - name: CALLEDPORT
          value: "8081"
        ports:
        - containerPort: 8081
          name: called
      
      
````````````````````````````````````````````````

apiVersion: v1
kind: Service              
metadata:
  name: caller-loadbalancer
spec:
  type: LoadBalancer       
  ports:
  - port: 80               
    targetPort: 8080        
  selector:            
    app: caller    
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: caller
  labels:
    app: caller
spec:
  replicas: 2                                             
  minReadySeconds: 15
  strategy:
    type: RollingUpdate                                   
    rollingUpdate: 
      maxUnavailable: 1                                   
      maxSurge: 1                                         
  selector:
    matchLabels:
      app: caller
      tier: caller-called
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: caller
        tier: caller-called
    spec:
      containers:
      - image: alessandroargentieri/caller-caller
        name: caller
        env:
        - name: CALLERPORT
          value: "8080"
        - name: CALLEDADDRESS
          value: "http://called-loadbalancer"
        ports:
        - containerPort: 8080
          name: caller
          
  ````````````````````````````````````````````````````        
