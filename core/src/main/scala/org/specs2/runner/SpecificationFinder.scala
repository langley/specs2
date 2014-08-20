package org.specs2
package runner

import java.util.regex._
import control._
import reflect.Classes
import specification.core._
import text.SourceFile._
import io._
import scalaz.std.anyVal._
import scalaz.syntax.bind._
import scalaz.syntax.traverse._
import scalaz.std.list._

/**
 * This trait loads specifications found on a given source directory based
 * on a regular expression representing the Specification name, usually .*Spec
 */
trait SpecificationsFinder {

  /**
   * @param path a path to a directory containing scala files (it can be a glob: i.e. "dir/**/*spec.scala")
   * @param pattern a regular expression which is supposed to match an object name extending a Specification
   * @param filter a function to filter out unwanted specifications
   * @return specifications created from specification names
   */
  def findSpecifications(path: String              = "**/*.scala",
                         pattern: String           = ".*Spec",
                         filter: String => Boolean = { (name: String) => true },
                         basePath: DirectoryPath   = DirectoryPath.unsafe(new java.io.File("src/test/scala").getAbsolutePath),
                         verbose: Boolean          = false,
                         classLoader: ClassLoader  = Thread.currentThread.getContextClassLoader,
                         fileSystem: FileSystem    = FileSystem): Action[List[SpecificationStructure]] =
    specificationNames(path, pattern, basePath, fileSystem, verbose).flatMap { names =>
      names.filter(filter).map(n => SpecificationStructure.create(n, classLoader)).toList.sequenceU
    }

  /**
   * @param path a path to a directory containing scala files (it can be a glob: i.e. "dir/**/*spec.scala")
   * @param pattern a regular expression which is supposed to match an object name extending a Specification
   * @param filter a function to filter out unwanted specifications
   * @return specifications created from specification names
   */
  def specifications(path: String              = "**/*.scala",
                     pattern: String           = ".*Spec",
                     filter: String => Boolean = { (name: String) => true },
                     basePath: DirectoryPath   = DirectoryPath.unsafe(new java.io.File("src/test/scala").getAbsolutePath),
                     verbose: Boolean          = false,
                     classLoader: ClassLoader  = Thread.currentThread.getContextClassLoader,
                     fileSystem: FileSystem    = FileSystem): Seq[SpecificationStructure] =
    findSpecifications(path, pattern, filter, basePath, verbose, classLoader, fileSystem)
      .execute(if (verbose) consoleLogging else noLogging)
      .unsafePerformIO().toEither.fold(
        e   => { if (verbose) println(e); Seq() },
        seq => seq)

  /**
   * @param pathGlob a path to a directory containing scala files (it can be a glob: i.e. "dir/**/*spec.scala")
   * @param pattern a regular expression which is supposed to match an object name extending a Specification
   * @return specification names by scanning files and trying to find specifications declarations
   */
  def specificationNames(pathGlob: String, pattern: String, basePath: DirectoryPath, fileSystem: FileSystem, verbose: Boolean) : Action[List[String]] = {
    lazy val specClassPattern = {
      val p = specPattern("class", pattern)
      log("\nthe pattern used to match specification classes is: "+p+"\n", verbose) >>
        Actions.safe(Pattern.compile(p))
    }

    lazy val specObjectPattern = {
      val p = specPattern("object", pattern)
      log("\nthe pattern used to match specification objects is: "+p+"\n", verbose) >>
        Actions.safe(Pattern.compile(p))
    }

    for {
      objectPattern <- specObjectPattern
      classPattern  <- specClassPattern
      paths         <- fileSystem.filePaths(basePath, pathGlob, verbose)
    } yield paths.toList.map(path => readClassNames(path, objectPattern, classPattern, fileSystem, verbose)).sequenceU.map(_.flatten)
  }.flatMap[List[String]](identity)

  /**
   * Read the content of the file at 'path' and return all names matching the object pattern
   * or the class pattern
   */
  def readClassNames(path: FilePath, objectPattern: Pattern, classPattern: Pattern, fileSystem: FileSystem, verbose: Boolean): Action[Seq[String]] = {
    for {
      fileContent <- fileSystem.readFile(path)
      packName = packageName(fileContent)
      _  <- log("\nSearching for specifications in file: "+path, verbose)
    } yield (classNames(packName, fileContent, objectPattern, "$", verbose) |@| classNames(packName, fileContent, classPattern, "", verbose))(_ ++ _)
  }.flatMap(identity)

  /**
   * pattern to use to get specification names from file contents
   */
  def specPattern(specType: String, pattern: String) = "\\s*"+specType+"\\s*(" + pattern + ")\\s*extends\\s*.*"
}

object SpecificationsFinder extends SpecificationsFinder

