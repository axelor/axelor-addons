Prestashop
================================

PrestaShop is a freemium, open source e-commerce platform. Prestashop module of axelor addons, allows an integration between Axelor and Prestashop.

Local Installation
================================

Versions:
* Prestashop: 8.1.2 or above
* MySQL: 5.7

Steps to install Prestashop using docker
1. Create a docker network
2. Run mysql docker image linking it to network created
3. Run prestashop docker image linking it to mysql database and created network

```bash
docker network create prestashop
docker run -ti --name mysql --network prestashop -e MYSQL_ROOT_PASSWORD=admin -p 3307:3306 -d mysql:5.7
docker run -ti --name my-ps --network prestashop -e DB_SERVER=mysql -p 8085:80 -d prestashop/prestashop:8.1.2
```
Start the Prestashop's installation by accessing `"http://localhost:8085/"`

Once the installation is complete, remove the `install` directory and rename `admin` directory in `my-ps` docker container

```bash
docker exec -it my-ps /bin/bash
rm -rf install
mv admin/ axelor/
```

Prestashop's back-office can then be managed by accessing `"http://localhost:8085/axelor"` 

Things to be taken care of
================================
* Sale order in Prestashop does not allow multiple sale order lines (cart rows in prestashop) with same product. Such orders won't get exported to Prestashop.