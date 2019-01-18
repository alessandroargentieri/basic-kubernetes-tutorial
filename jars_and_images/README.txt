
Usando solo i JAR, senza dockerfile o kubernetes dobbiamo andare nella cartella called:

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

# deployment-called.yaml
~~~~~~~~~~~~~~~
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
#deployment-caller
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

~~~~~~~~~~~~~~~~~ALTRE INFO


l'architettura consta di:

- front end in Vue js in cui scriviamo la mail e inviamo la richiesta tramite POST per visualizzare le info utente e il carrello ad esso associato
- microservizio aggregatore che ricevuta la mail invia una richiesta al microservizio dell'utente e, se esiste, invia la richiesta al microservizio del carrello per aggregare i dati
- microservizio che recupera i dati dell'utente
- microservizio che recupera il carrello associato all'utente

Ho creato i singoli servizi e provati in locale 
Ho fatto ./mvnw clean package per creare i jar in target di ogni microservizio
Ho creato i Dockerfile in ogni cartella di ogni microservizio (fare attenzione!)

Per i servizi con spring boot mi servono le variabili d'ambiente (anche in application.properties e con @Value) ed uso java 8 alpine come immagine di base e il fat jar generato prima con il maven package.
Per il front end metto tutto nella cartella build e poi chiedo a docker di copiarla e inserirla in /html dell'immagine base di nginx che verrà scaricata tramite le istruzioni contenute nel Dockerfile.
Avvio Docker

Creiamo le immagini usando i dockerfile

vado nella cartella dell'aggregator (non dimenticare il punto al termine dell'istruzione seguente):
docker build -f Dockerfile -t alessandroargentieri/dockerize-aggregator .

vado nella cartella dello user service (non dimenticare il punto al termine dell'istruzione seguente):
docker build -f Dockerfile -t alessandroargentieri/dockerize-user-service .

vado nella cartella del chart service (non dimenticare il punto al termine dell'istruzione seguente):
docker build -f Dockerfile -t alessandroargentieri/dockerize-chart-service .

vado nella cartella del front-end (non dimenticare il punto al termine dell'istruzione seguente):
docker build -f Dockerfile -t alessandroargentieri/dockerize-frontend .

controllo le immagini create:
docker images

Ora che ho le immagini voglio creare una subnet per dare ai miei container (che creero') un IP statico.

controllo le subnet presenti:
docker network ls

cancello la rete che usa la mia stessa subnet (se esiste e non mi serve):
docker network rm <sha>

creo una nuova subnet che chiamo docknet:
docker network create --subnet=172.18.0.0/16 docknet

Adesso creo e mando in run i container partendo dalle immagini create prima.
In quest caso non ho bisogno di essere nella cartella dei singoli progetti perchè agisco con Docker a livello globale.
Ad ogni container che creo specifico la subnet, l'IP statico, la porta di ascolto interna ed esterna, le eventuali variabili d'ambiente ed ovviamente l'immagine che voglio utilizzare:

docker run --net docknet --ip 172.18.0.27 -d -p 80:80 alessandroargentieri/dockerize-frontend
docker run --net docknet --ip 172.18.0.22 -d -p 8081:8081 alessandroargentieri/dockerize-user-service
docker run --net docknet --ip 172.18.0.23 -d -p 8082:8082 alessandroargentieri/dockerize-chart-service
docker run --net docknet --ip 172.18.0.20 -d -p 8080:8080 -e AGGREGATOR_USER_SERVICE_ADDRESS='http://172.18.0.22:8081/user' -e AGGREGATOR_CHART_SERVICE_ADDRESS='http://172.18.0.23:8082/chart' alessandroargentieri/dockerize-aggregator

Ora i container sono avviati e posso controllare con 
docker ps

I container totali (avviati e stoppati sono)
docker ps -a

Posso stopparli con:
docker stop <sha>

Farli ripartire con:
docker start <sha>

eliminarli con:
docker rm <sha>

le immagini sono:
docker images

le immagini possono essere cancellate con:
docker rmi <sha>

posso ispezionare un container con:
docker inspect <sha>

Bando alle ciance, il nostro frontend ci aspetta (se i container sono avviati) all'indirizzo http://localhost:80

Se volessi eliminare tutti i container in docker:
docker rm `docker ps --no-trunc -aq`

PS: se volessi pushare le immagini create su DockerHub basta fare l'accesso:
docker login -u="$USER_ID" -p="$USER_PWD"

push dell'immagine su dockerhub:
docker push $USER_ID/nomeimmagine

pull dell'immagine da dockerhub (quando ci servirà):
docker pull $USER_ID/nomeimmagine


