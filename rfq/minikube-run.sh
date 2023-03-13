if ! [ -x "$(command -v docker)" ]; then
  echo 'Error: docker is not installed. Please install it first.' >&2
  exit 1
fi

if ! [ -x "$(command -v minikube)" ]; then
  echo 'Error: minikube is not installed. Please install it first.' >&2
  exit 1
fi

if ! [ -x "$(command -v kubectl)" ]; then
  echo 'Error: kubectl is not installed. Please install it first.' >&2
  exit 1
fi

minikubestatus=$(minikube status -f "{{.Host}}")
if [[ $minikubestatus != *"Running"* ]]; then
  echo "Minikube is not running; please start with at least 15GB ram and 6 cores assigned."
  exit 1
fi

echo "➡️  Building Java..."
./gradlew
echo "➡️  Building admin docker image..."
cd admin || exit
docker build . -t admin --no-cache
echo "➡️  Building cluster image..."
cd ../cluster || exit
docker build . -t cluster --no-cache
cd .. || exit
echo "➡️  Removing old kubernetes namespaces (if they exist)..."
kubectl delete ns aeron-io-sample-cluster
kubectl delete ns aeron-io-sample-admin
echo "➡️  Loading docker images into minikube..."
minikube image load admin:latest
minikube image load cluster:latest
echo "➡️  Applying admin..."
cd ./kubernetes/admin || exit
kubectl apply -f .
echo "➡️  Applying cluster..."
cd ../cluster || exit
kubectl apply -f .
echo "➡️  Done"