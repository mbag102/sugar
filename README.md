#Info
This is a fork from the original Sugar Orm. I have added many of my own features and will be maintaining this project for the forseable future.

##Features
- Object relationships (One-to-One, One-to-Many, Many-To-One, and Many-To-Many)
- Specifying a custom field to be the primary key (@Id annotation)
- Helper query methods such as findUnique and findByRelationship
- Object Cascading
- Lazy loading of objects

##Future features
- Eager loading of objects

Let me know what features are important or bugs you encounter and I will do my best to fix them. I use this in many of my own projects and have found great success.

###For those of you looking to use this DB for offline data storage (caching), Check out [Spice] (http://github.com/mbag102/spice)


# Sugar ORM [![Build Status](https://travis-ci.org/satyan/sugar.svg?branch=master)](https://travis-ci.org/satyan/sugar) [![Coverage Status](https://coveralls.io/repos/satyan/sugar/badge.svg?branch=master)](https://coveralls.io/r/satyan/sugar?branch=master)

[![Join the chat at https://gitter.im/satyan/sugar](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/satyan/sugar?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Insanely easy way to work with Android databases.

Official documentation can be found [here](http://satyan.github.io/sugar) - Check some examples below. The example application is provided in the **example** folder in the source.

## Features

Sugar ORM was built in contrast to other ORM's to have:

- A simple, concise, and clean integration process with minimal configuration.
- Automatic table and column naming through reflection.
- Support for migrations between different schema versions.

## Installing

There are four ways to install Sugar:

#### As a Gradle dependency

This is the preferred way. Simply add:

```groovy
compile 'com.github.satyan:sugar:1.4'
```

to your project dependencies and run `gradle build` or `gradle assemble`.

#### As a Maven dependency

Declare the dependency in Maven:

```xml
<dependency>
    <groupId>com.github.satyan</groupId>
    <artifactId>sugar</artifactId>
    <version>1.4</version>
</dependency>
```

#### As a library project

Download the source code and import it as a library project in Eclipse. The project is available in the folder **library**. For more information on how to do this, read [here](http://developer.android.com/tools/projects/index.html#LibraryProjects).

#### As a jar

Visit the [releases](https://github.com/satyan/sugar/releases) page to download jars directly. You can drop them into your `libs` folder and configure the Java build path to include the library. See this [tutorial](http://www.vogella.com/tutorials/AndroidLibraryProjects/article.html) for an excellent guide on how to do this.


### How to use master version
First, download sugar repository
```
git clone git@github.com:satyan/sugar.git
```

include this in your **settings.gradle**
```gradle
include ':app' // your module app
include ':sugar'

def getLocalProperty(prop) {
	Properties properties = new Properties()
	properties.load(new File(rootDir.absolutePath + '/local.properties').newDataInputStream())
	return properties.getProperty(prop, '')
}

project(':sugar').projectDir = new File(getLocalProperty('sugar.dir'))

```

include this in your **local.properties**
```
sugar.dir=/path/to/sugar/library
```

add sugar project to the dependencies of your main project (build.gradle)
```gradle
dependencies {
    compile project(':sugar')
}
```

===================

After installing, check out how to set up your first database and models [here](http://satyan.github.io/sugar/getting-started.html) **Outdated**. Check examples of 1.4 and master below: 

## Examples
### SugarRecord
```java
public class Book extends SugarRecord {
  @Unique
  String isbn;
  String title;
  String edition;

  // Default constructor is necessary for SugarRecord
  public Book() {

  }

  public Book(String isbn, String title, String edition) {
    this.isbn = isbn;
    this.title = title;
    this.edition = edition;
  }
}
```
or
```java
@Table
public class Book { ... }
```

### Save Entity
```java
Book book = new Book("isbn123", "Title here", "2nd edition")
book.save();
```

### Load Entity
```java
Book book = Book.findById(Book.class, 1);
```

### Update Entity
```java
Book book = Book.findById(Book.class, 1);
book.title = "updated title here"; // modify the values
book.edition = "3rd edition";
book.save(); // updates the previous entry with new values.
```

### Delete Entity
```java
Book book = Book.findById(Book.class, 1);
book.delete();
```

### Update Entity based on Unique values
```java
Book book = new Book("isbn123", "Title here", "2nd edition")
book.save();

// Update book with isbn123
Book sameBook = new Book("isbn123", "New Title", "5th edition")
sameBook.update();

book.getId() == sameBook.getId(); // true
```

### Bulk Insert
```java
List<Book> books = new ArrayList<>();
books.add(new Book("isbn123", "Title here", "2nd edition"))
books.add(new Book("isbn456", "Title here 2", "3nd edition"))
books.add(new Book("isbn789", "Title here 3", "4nd edition"))
SugarRecord.saveInTx(books);
```

### When using ProGuard
```java
# Ensures entities remain un-obfuscated so table and columns are named correctly
-keep class com.yourpackage.yourapp.domainclasspackage.** { *; }
```

## [CHANGELOG](https://github.com/satyan/sugar/blob/master/CHANGELOG.md)

## Contributing

Please fork this repository and contribute back using [pull requests](https://github.com/satyan/sugar/pulls). Features can be requested using [issues](https://github.com/satyan/sugar/issues). All code, comments, and critiques are greatly appreciated.
