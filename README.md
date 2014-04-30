# AWS Maven Wagon
This project is a [Maven Wagon][wagon] for [Amazon S3][s3].  In order to to publish artifacts to an S3 bucket, the user (as identified by their access key) must be listed as an owner on the bucket.

## Why this fork?
This fork's enhancement is the ability to customize the AWS credentials 
provider chain that is used to resolve credentials for the S3 bucket that hosts
the Maven artifacts you deploy. The upstream version uses a default provider 
chain that includes an `InstanceProfileCredentialsProvider` that, in my 
opinion, just takes too darn long to realize it's not going to be able to 
resolve any credentials and should give up.

## Usage
To publish Maven artifacts to S3 a build extension must be defined in a project's `pom.xml`.  The latest version of the wagon can be found in the `mvn-repo` branch of this repository.

```xml
<project>
  ...
  <repositories>
    ...
    <repository>
      <id>mike10004-snapshots</id>
      <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    </repository>
    ...
  </repositories>
  ...
  <build>
    ...
    <extensions>
      ...
      <extension>
        <groupId>com.github.mike10004</groupId>
        <artifactId>aws-maven</artifactId>
        <version>5.1.1</version>
      </extension>
      ...
    </extensions>
    ...
  </build>
  ...
</project>
```

Once the build extension is configured distribution management repositories can be defined in the `pom.xml` with an `s3://` scheme.

```xml
<project>
  ...
  <distributionManagement>
    <repository>
      <id>aws-release</id>
      <name>AWS Release Repository</name>
      <url>s3://<BUCKET>/release</url>
    </repository>
    <snapshotRepository>
      <id>aws-snapshot</id>
      <name>AWS Snapshot Repository</name>
      <url>s3://<BUCKET>/snapshot</url>
    </snapshotRepository>
  </distributionManagement>
  ...
</project>
```

Finally the `~/.m2/settings.xml` must be updated to include access and secret keys for the account. The access key should be used to populate the `username` element, and the secret access key should be used to populate the `password` element.

```xml
<settings>
  ...
  <servers>
    ...
    <server>
      <id>aws-release</id>
      <username>0123456789ABCDEFGHIJ</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCD</password>
    </server>
    <server>
      <id>aws-snapshot</id>
      <username>0123456789ABCDEFGHIJ</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCD</password>
    </server>
    ...
  </servers>
  ...
</settings>
```

## Customizing the credentials provider chain

To authenticate in AWS so that you can deploy artifacts to your S3 bucket, the 
AWS SDK traverses a (credentials provider chain)[http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/AWSCredentialsProviderChain.html], 
which is a sequence of objects that may or may not be able to pass credentials
along. By default, the chain checks environment variables, system properties,
the EC2 Instance Metadata Service, and then the username and password you have
specified for your repository in the `<server>` entry in `~/.m2/settings.xml`. 

To customize the credentials provider chain, edit that server entry in 
`~/.m2/settings.xml` to include a `<configuration><credentialsProviders>` setting
whose value is one or more of the following credentials provider specification
tokens:

 - `EnvironmentVariable` - use an (EnvironmentVariableCredentialsProvider)[http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/EnvironmentVariableCredentialsProvider.html] that checks `AWS_ACCESS_KEY_ID` and `AWS_SECRET_KEY` environment variables
 - `SystemProperties` - use a (SystemPropertiesCredentialsProvider)[http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/SystemPropertiesCredentialsProvider.html] that checks the `aws.accessKeyId` and `aws.secretKey` Java system properties
 - `InstanceProfile` - use an (InstanceProfileCredentialsProvider)[http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/InstanceProfileCredentialsProvider.html] that tries to load credentials from the Amazon EC2 Instance Metadata Service
 - `AuthenticationInfo` - use an `org.springframework.build.aws.maven.AuthenticationInfoAWSCredentialsProvider` that gets credentials from the `<server>` username and password settings from `~/.m2/settings.xml`

Delimit multiple tokens with commas. For example, if you only want to try providing 
credentials first through environment variables and next through authentication info,
then the server entry would look like 

```xml
<settings>
  ...
  <servers>
    ...
    <server>
      <id>aws-snapshot</id>
      <username>0123456789ABCDEFGHIJ</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCD</password>
      <configuration>
        <credentialsProviders>EnvironmentVariable,AuthenticationInfo</credentialsProviders>
      </configuration>
    </server>
    ...
  </servers>
  ...
</settings>
```

If you do not specify the credentials providers configuration element, then the default
chain mentioned above will be used.

## Making Artifacts Public
This wagon doesn't set an explict ACL for each artfact that is uploaded.  Instead you should create an AWS Bucket Policy to set permissions on objects.  A bucket policy can be set in the [AWS Console][console] and can be generated using the [AWS Policy Generator][policy-generator].

In order to make the contents of a bucket public you need to add statements with the following details to your policy:

| Effect  | Principal | Action       | Amazon Resource Name (ARN)
| ------- | --------- | ------------ | --------------------------
| `Allow` | `*`       | `ListBucket` | `arn:aws:s3:::<BUCKET>`
| `Allow` | `*`       | `GetObject`  | `arn:aws:s3:::<BUCKET>/*`

If your policy is setup properly it should look something like:

```json
{
  "Id": "Policy1397027253868",
  "Statement": [
    {
      "Sid": "Stmt1397027243665",
      "Action": [
        "s3:ListBucket"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::<BUCKET>",
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    },
    {
      "Sid": "Stmt1397027177153",
      "Action": [
        "s3:GetObject"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::<BUCKET>/*",
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    }
  ]
}
```

If you prefer to use the [command line][cli], you can use the following script to make the contents of a bucket public:

```bash
BUCKET=<BUCKET>
TIMESTAMP=$(date +%Y%m%d%H%M)
POLICY=$(cat<<EOF
{
  "Id": "public-read-policy-$TIMESTAMP",
  "Statement": [
    {
      "Sid": "list-bucket-$TIMESTAMP",
      "Action": [
        "s3:ListBucket"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::$BUCKET",
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    },
    {
      "Sid": "get-object-$TIMESTAMP",
      "Action": [
        "s3:GetObject"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::$BUCKET/*",
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    }
  ]
}
EOF
)

aws s3api put-bucket-policy --bucket $BUCKET --policy "$POLICY"
```

[aws-maven]: http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.springframework.build%22%20AND%20a%3A%22aws-maven%22
[cli]: http://aws.amazon.com/documentation/cli/
[console]: https://console.aws.amazon.com/s3
[policy-generator]: http://awspolicygen.s3.amazonaws.com/policygen.html
[s3]: http://aws.amazon.com/s3/
[wagon]: http://maven.apache.org/wagon/
