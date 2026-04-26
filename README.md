# Wordsmith DevOps Project

This repository contains a beginner-friendly but production-structured DevOps portfolio project for the Wordsmith microservices app. The stack runs three services:

- `web`: Go frontend
- `words`: Java API that serves random words
- `redis`: Redis datastore used by the API

The delivery pipeline covers local Docker development, Docker Hub publishing, Kubernetes deployment, AWS EC2 provisioning with Terraform, GitHub Actions CI, and ArgoCD GitOps continuous delivery.

## Group Members

- Member 1: Name: Mesam E Tamaar  |  Roll No: 22i-1304  | Cloud Computing (CS-B)
- Member 2: Name: Tashfeen Hassan  |  Roll No: 22i-0860  | Cloud Computing (CS-B)

## Project Structure

```text
project/
├── web/
├── words/
├── redis/
├── k8s/
│   ├── web-deployment.yaml
│   ├── web-service.yaml
│   ├── api-deployment.yaml
│   ├── api-service.yaml
│   ├── redis-deployment.yaml
│   └── redis-service.yaml
├── terraform/
│   └── main.tf
├── argocd/
│   └── application.yaml
├── .github/workflows/
│   └── ci.yml
├── docker-compose.yml
└── README.md
```

## Architecture

```text
                         +-----------------------------+
                         |         GitHub Repo         |
                         |  source + k8s manifests     |
                         +-------------+---------------+
                                       |
                                       v
                            +----------+-----------+
                            |    GitHub Actions    |
                            | build + push images  |
                            +----------+-----------+
                                       |
                                       v
                         +-------------+--------------+
                         |          Docker Hub        |
                         | wordsmith-web/wordsmith-api|
                         +-------------+--------------+
                                       |
                     +-----------------+------------------+
                     |                                    |
                     v                                    v
          +----------+-----------+             +----------+-----------+
          |     ArgoCD Server    |             | Terraform on AWS     |
          |   watches Git repo   |             | provisions EC2 host  |
          +----------+-----------+             +----------+-----------+
                     |                                    |
                     +-----------------+------------------+
                                       |
                                       v
                         +-------------+--------------+
                         |       Kubernetes Cluster   |
                         | web NodePort :30007        |
                         | api ClusterIP              |
                         | redis ClusterIP            |
                         +-------------+--------------+
                                       |
                                       v
                          Browser -> http://EC2_IP:30007
```

## End-to-End Flow

```text
GitHub push
-> GitHub Actions builds Docker images
-> Docker Hub stores images
-> GitHub Actions updates k8s image tags with commit SHA
-> ArgoCD detects manifest changes and syncs the cluster
-> Kubernetes pulls images
-> Application runs on EC2 cluster
-> Access via NodePort 30007
```

## Tech Stack

- Docker and Docker Compose
- Docker Hub
- Kubernetes
- ArgoCD
- Terraform
- AWS EC2
- GitHub Actions
- Go
- Java 17
- Redis

## Service Configuration

The application uses environment variables for service discovery:

- `web`
  - `WORDS_HOST`
  - `WORDS_PORT`
  - `WEB_PORT`
- `words`
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `SERVICE_PORT`

## Local Development with Docker

Build and run the complete stack locally:

```bash
docker compose up --build
```

Access the app at:

```text
http://localhost:8080
```

Optional cleanup:

```bash
docker compose down
```

## Docker Hub Publishing

The repository is configured with Docker Hub namespace `mtk21` in:

- `docker-compose.yml`
- `k8s/web-deployment.yaml`
- `k8s/api-deployment.yaml`

The ArgoCD application points to:

```text
https://github.com/mesametamaarkhan/wordsmith.git
```

Manual build and push example:

```bash
export DOCKER_USERNAME=mtk21
export GIT_SHA=$(git rev-parse --short HEAD)

docker build -t $DOCKER_USERNAME/wordsmith-web:latest -t $DOCKER_USERNAME/wordsmith-web:$GIT_SHA ./web
docker build -t $DOCKER_USERNAME/wordsmith-api:latest -t $DOCKER_USERNAME/wordsmith-api:$GIT_SHA ./words

docker push $DOCKER_USERNAME/wordsmith-web:latest
docker push $DOCKER_USERNAME/wordsmith-web:$GIT_SHA
docker push $DOCKER_USERNAME/wordsmith-api:latest
docker push $DOCKER_USERNAME/wordsmith-api:$GIT_SHA
```

## Kubernetes Deployment

Apply the Kubernetes resources:

```bash
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/redis-service.yaml
kubectl apply -f k8s/api-deployment.yaml
kubectl apply -f k8s/api-service.yaml
kubectl apply -f k8s/web-deployment.yaml
kubectl apply -f k8s/web-service.yaml
```

Check the workload status:

```bash
kubectl get deployments
kubectl get services
kubectl get pods
```

Application access:

```text
http://<EC2_PUBLIC_IP>:30007
```

## Terraform on AWS EC2

The Terraform configuration provisions:

- 1 Ubuntu 22.04 EC2 instance
- `t3.small` instance type
- inbound access for `22` and `30007`
- public IP output

Initialize and apply:

```bash
cd terraform
terraform init
terraform apply -var="key_name=your-keypair-name"
```

Example output:

```text
public_ip = 54.xx.xx.xx
```

SSH into the instance after `terraform apply`:

```bash
ssh -i /path/to/your-key.pem ubuntu@<EC2_PUBLIC_IP>
```

Bootstrap MicroK8s on the EC2 host:

```bash
chmod +x scripts/bootstrap-microk8s.sh
scp -i /path/to/your-key.pem scripts/bootstrap-microk8s.sh ubuntu@<EC2_PUBLIC_IP>:~
ssh -i /path/to/your-key.pem ubuntu@<EC2_PUBLIC_IP>
./bootstrap-microk8s.sh
exit
ssh -i /path/to/your-key.pem ubuntu@<EC2_PUBLIC_IP>
microk8s kubectl get nodes
```

The bootstrap script installs MicroK8s from the official snap, enables `dns` and `hostpath-storage`, and prepares the `ubuntu` user for `microk8s kubectl` access.

## GitHub Actions CI

The workflow at `.github/workflows/ci.yml` runs on pushes to `main` and:

- logs in to Docker Hub using `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`
- builds the `web` and `words` container images
- pushes both `latest` and commit SHA tags
- updates the Kubernetes deployment manifests to the new commit SHA
- pushes the manifest change back to `main` for ArgoCD to reconcile

Required repository secrets:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

## ArgoCD GitOps CD

### Install ArgoCD

For a single-node EC2 setup, install MicroK8s first and use its bundled `kubectl`. The official MicroK8s docs recommend:

```bash
sudo snap install microk8s --classic --channel=1.33
sudo usermod -a -G microk8s $USER
mkdir -p ~/.kube
chmod 0700 ~/.kube
su - $USER
microk8s status --wait-ready
microk8s enable dns
microk8s enable hostpath-storage
```

Create the namespace and install the official manifests:

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

Expose the ArgoCD API server with port-forward:

```bash
kubectl port-forward svc/argocd-server -n argocd 8088:443
```

Or patch it to NodePort:

```bash
kubectl patch svc argocd-server -n argocd -p '{"spec":{"type":"NodePort"}}'
```

Retrieve the initial admin password:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

Check that ArgoCD is healthy:

```bash
kubectl get pods -n argocd
kubectl get svc -n argocd
```

### Create the ArgoCD Application

The ArgoCD application manifest is stored at `argocd/application.yaml`. Apply it with:

```bash
kubectl apply -f argocd/application.yaml
```

Then confirm sync status:

```bash
kubectl get applications -n argocd
kubectl describe application wordsmith -n argocd
```

This application is configured with:

- automatic sync enabled
- self-healing enabled
- prune enabled
- source path set to `/k8s`

## Deployment Order

1. Push code to GitHub.
2. GitHub Actions builds and pushes updated images to Docker Hub.
3. GitHub Actions writes the new commit SHA into the Kubernetes manifests and pushes that change to `main`.
4. ArgoCD detects the Git change in `main`.
5. ArgoCD syncs the cluster automatically.
6. Kubernetes pulls the new SHA-tagged images.
7. Users access the app on `http://<EC2_PUBLIC_IP>:30007`.


## Notes

- The Kubernetes manifests intentionally use only `Deployment` and `Service` resources.
- The Redis dataset is seeded automatically by the `words` API on startup.
- MicroK8s and ArgoCD installation commands are based on the official MicroK8s and ArgoCD getting-started documentation.
