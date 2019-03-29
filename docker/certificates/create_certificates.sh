#!/bin/bash -e

# Most of the content pulled from https://raymii.org/s/tutorials/OpenSSL_command_line_Root_and_Intermediate_CA_including_OCSP_CRL%20and_revocation.html
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
SUBJ=${SUBJ:-/C=US/ST=Wisconsin/L=Middleon/O=US Geological Survey/OU=WMA/CN=*}
rm -rf $DIR/root
rm -rf $DIR/intermediate1
mkdir $DIR/root
mkdir $DIR/intermediate1
cp $DIR/openssl-root.conf $DIR/root/openssl.conf
cp $DIR/openssl-intermediate.conf $DIR/intermediate1/openssl.conf

touch $DIR/root/certindex
echo 1000 > $DIR/root/certserial
echo 1000 > $DIR/root/crlnumber

echo "Generating RSA private key @ $DIR/root/rootca.key ..."
openssl genrsa -out $DIR/root/rootca.key 8192

echo "Generating self-signed root CA @ $DIR/root/rootca.crt ..."
openssl req -sha256 -new -x509 -days 9999 -key $DIR/root/rootca.key -out $DIR/root/rootca.crt -subj "$SUBJ"

echo "Generating the intermediate CA's private key @ $DIR/root/intermediate1.key ..."
openssl genrsa -out $DIR/root/intermediate1.key 4096

echo "Generating the intermediate1 CA's CSR @ $DIR/root/intermediate1.csr ..."
openssl req -new -sha256 -key $DIR/root/intermediate1.key -out $DIR/root/intermediate1.csr -subj "$SUBJ"

cd $DIR/root

echo "Signing the intermediate1 CSR with the Root CA, outputting to $DIR/root/intermediate1.crt ..."
openssl ca -batch -config $DIR/root/openssl.conf -notext -in $DIR/root/intermediate1.csr -out $DIR/root/intermediate1.crt

echo "Generating the CRL (both in PEM and DER) @ $DIR/root/rootca.crl.pem and $DIR/root/rootca.crl respectively ..."
openssl ca -config $DIR/root/openssl.conf -gencrl -keyfile $DIR/root/rootca.key -cert $DIR/root/rootca.crt -out $DIR/root/rootca.crl.pem
openssl crl -inform PEM -in $DIR/root/rootca.crl.pem -outform DER -out $DIR/root/rootca.crl

cd $DIR
cp $DIR/root/intermediate1.key $DIR/intermediate1/
cp $DIR/root/intermediate1.crt $DIR/intermediate1/
cp $DIR/root/rootca.crt $DIR/intermediate1/
cp $DIR/root/rootca.key $DIR/intermediate1/

mkdir $DIR/intermediate1/end_user_certs
touch $DIR/intermediate1/certindex
echo 1000 > $DIR/intermediate1/certserial
echo 1000 > $DIR/intermediate1/crlnumber

cd $DIR/intermediate1
echo "Generating an empty CRL (both in PEM and DER) @ $DIR/intermediate1/rootca.crl.pem and $DIR/intermediate1/rootca.crl respectively ..."
openssl ca -config $DIR/intermediate1/openssl.conf -gencrl -keyfile $DIR/intermediate1/rootca.key -cert $DIR/intermediate1/rootca.crt -out $DIR/intermediate1/rootca.crl.pem
openssl crl -inform PEM -in $DIR/intermediate1/rootca.crl.pem -outform DER -out $DIR/intermediate1/rtca.crl

echo "Generating end user RSA key @ $DIR/intermediate1/end_user_certs/tomcat-wildcard.key"
openssl genrsa -out $DIR/intermediate1/end_user_certs/tomcat-wildcard.key 4096 -subj "$SUBJ"
echo "Generating end user certificate signing request @ $DIR/intermediate1/end_user_certs/tomcat-wildcard.csr"
openssl req -new -sha256 -key $DIR/intermediate1/end_user_certs/tomcat-wildcard.key -out $DIR/intermediate1/end_user_certs/tomcat-wildcard.csr -subj "$SUBJ"
echo "Signing end user CSR with intermediate certificate, putting output certificate @ $DIR/intermediate1/end_user_certs/tomcat-wildcard.crt"
openssl ca -batch -config $DIR/intermediate1/openssl.conf -notext -in $DIR/intermediate1/end_user_certs/tomcat-wildcard.csr -out $DIR/intermediate1/end_user_certs/tomcat-wildcard.crt

echo "Generating the CRL (both in PEM and DER) @ $DIR/intermediate1/intermediate1.crl.pem and $DIR/intermediate1/intermediate1.crl respectively"
openssl ca -config $DIR/intermediate1/openssl.conf -gencrl -keyfile $DIR/intermediate1/intermediate1.key -cert $DIR/intermediate1/intermediate1.crt -out $DIR/intermediate1/intermediate1.crl.pem
openssl crl -inform PEM -in $DIR/intermediate1/intermediate1.crl.pem -outform DER -out $DIR/intermediate1/intermediate1.crl

echo "Creating chain certificate @ $DIR/intermediate1/end_user_certs/tomcat-wildcard.chain ..."
cat $DIR/root/rootca.crt $DIR/intermediate1/intermediate1.crt > $DIR/intermediate1/end_user_certs/tomcat-wildcard.chain

cp $DIR/intermediate1/end_user_certs/* $DIR
