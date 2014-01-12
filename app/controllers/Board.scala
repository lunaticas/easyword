package controllers

import java.io.PrintWriter

import scala.collection.mutable.{Set => MSet}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.Play.current
import scala.concurrent.Future

import org.joda.time.DateTime

import com.typesafe.plugin._

import models._
import com.mongodb.casbah.Imports._

object Board extends Controller with Secured {
  implicit val objectIdReads = new Reads[ObjectId] {
    def reads(json: JsValue): JsResult[ObjectId] = {
      JsSuccess(new ObjectId(json.as[String]))
    }
  }

  implicit val objectIdWrites = new Writes[ObjectId] {
    def writes(id: ObjectId): JsValue = {
      Json.toJson(id.toString)
    }
  }

  val articleForm = Form(
    "text" -> text.verifying ("어려운 말이 들어있어요.", text => text.split("\\s").forall(SpellCheck.spellcheckLookup(_)))
  )

  val commentForm = Form(
    "text" -> text
  )

  def index = withUser { case (userId, user) => implicit request =>
    Ok(views.html.index(articleForm))
  }

  def create = withUser { case (userId, user) => implicit request =>
    articleForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.index(formWithErrors)),
      content => {
        Article.create(Article(userId, content, new DateTime()))
        Redirect(routes.Board.index).flashing(
          "success" -> "글을 저장했어요!"
        )
      }
    )
  }

  def createComment(authorId: String) = withUser { case (userId, user) => implicit request =>
    println("hi")
    commentForm.bindFromRequest.fold(
      formWithErrors => BadRequest(""),
      content => {
        Comment.create(Comment(
          userId,
          new ObjectId(authorId),
          content,
          new DateTime()
        ))
        Redirect(routes.Board.comments(authorId))
      }
    )
  }

  def articleToClient(articleId: ObjectId, article: Article, userId: ObjectId): JsValue = {
    val authorOpt = User.findById(article.authorId)
    val (authorEmail, authorName) = authorOpt match {
      case None => ("", "") // TODO
      case Some(author) => (author.email, author.name)
    }

    val numLikes = Article.numLikes(articleId)
    val numComments = Comment.numComments(articleId)
    val liked = Article.liked(userId, articleId)

    Json.toJson(Map(
      "id" -> Json.toJson(articleId),
      "content" -> Json.toJson(article.content),

      "authorEmail" -> Json.toJson(authorEmail),
      "authorName" -> Json.toJson(authorName),
      "authorId" -> Json.toJson(article.authorId),

      "created" -> Json.toJson(article.created),

      "numLikes" -> Json.toJson(numLikes),
      "numComments" -> Json.toJson(numComments),
      "liked" -> Json.toJson(liked)
    ))
  }

  def commentToClient(commentId: ObjectId, comment: Comment): JsValue = {
    val authorOpt = User.findById(comment.authorId)
    val (authorEmail, authorName) = authorOpt match {
      case None => ("", "") // TODO
      case Some(author) => (author.email, author.name)
    }

    Json.toJson(Map(
      "id" -> Json.toJson(commentId),
      "content" -> Json.toJson(comment.content),

      "authorEmail" -> Json.toJson(authorEmail),
      "authorName" -> Json.toJson(authorName),
      "authorId" -> Json.toJson(comment.authorId),

      "created" -> Json.toJson(comment.created)
    ))
  }

  def articles(skip: Long, limit: Long) = withUser { case (userId, user) => implicit request =>
    val reducedLimit = if (limit > 20) 20 else limit
    val articles = Article.findArticles(skip.asInstanceOf[Int], reducedLimit.asInstanceOf[Int])
    val result = articles.map { case (articleId, article) => articleToClient(articleId, article, userId) }
    Ok(Json.toJson(result))
  }

  def comments(articleId: String) = withUser { case (userId, user) => implicit request =>
    val comments = Comment.findCommentByArticle(new ObjectId(articleId))
    val result = comments.map { case (commentId, comment) => commentToClient(commentId, comment) }
    Ok(Json.toJson(result))
  }

  def like(id: String) = withUser { case (userId, user) => implicit request =>
    val articleId = new ObjectId(id)
    Article.like(articleId, userId)
    Article.findById(articleId) map { article =>
      Ok(articleToClient(articleId, article, userId))
    } getOrElse {
      BadRequest("")
    }
  }

  def unlike(id: String) = withUser { case (userId, user) => implicit request =>
    val articleId = new ObjectId(id)
    Article.unlike(articleId, userId)
    Article.findById(articleId) map { article =>
      Ok(articleToClient(articleId, article, userId))
    } getOrElse {
      BadRequest("")
    }
  }
}