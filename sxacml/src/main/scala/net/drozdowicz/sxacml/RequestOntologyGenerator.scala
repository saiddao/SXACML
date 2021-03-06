package net.drozdowicz.sxacml

import java.net.URI
import java.util.jar.Attributes

import org.apache.commons.validator.routines.UrlValidator
import org.semanticweb.owlapi.io.SystemOutDocumentTarget
import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.model.parameters.Imports

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.net.drozdowicz.sxacml.Constants

/**
  * Created by michal on 2015-03-18.
  */
object RequestOntologyGenerator {
  private val idAttributes = Array(
    "urn:oasis:names:tc:xacml:1.0:subject:subject-id",
    "urn:oasis:names:tc:xacml:1.0:resource:resource-id",
    "urn:oasis:names:tc:xacml:1.0:action:action-id"
  )

  private val categoryAssignmentProperties = Map(
    new URI("urn:oasis:names:tc:xacml:1.0:subject-category:access-subject") -> new URI("https://w3id.org/sxacml/access-control#requestedBy"),
    new URI("urn:oasis:names:tc:xacml:3.0:attribute-category:resource") -> new URI("https://w3id.org/sxacml/access-control#concernsResource"),
    new URI("urn:oasis:names:tc:xacml:3.0:attribute-category:action") -> new URI("https://w3id.org/sxacml/access-control#concernsAction"),
    new URI("urn:oasis:names:tc:xacml:3.0:attribute-category:environment") -> new URI("https://w3id.org/sxacml/access-control#inContextOf")
  )

  def getCategoryIndividualIds(ontologyId: String, requestId: String, attributes: Seq[ContextAttributeValue]) = {
    val categoryAttributes = attributes.groupBy(at => at.categoryId).toMap

    categoryAssignmentProperties.keySet.map(cat => {
      (cat, if (categoryAttributes.contains(cat))
        getCategoryIndividualUri(ontologyId, requestId, categoryAttributes(cat)) else
        getNewCategoryIndividualUri(ontologyId, requestId, cat.toString))
    }).toMap
  }

  def getRequestIndividualId(ontologyId: String, requestId: String) = {
    getNewCategoryIndividualUri(ontologyId, requestId, Constants.REQUEST_CLASS_ID)
  }

  def createOntologyId(sessionId: Option[String], requestId: String) = "https://w3id.org/sxacml/request/" + sessionId.getOrElse(requestId)

  def convertToOntology(owlManager: OWLOntologyManager)(sessionId: Option[String], requestId: String, requestAttributes: Seq[ContextAttributeValue], otherOntologies: Set[IRI]): OWLOntology = {
    val requestAttributesOnlyCategories = requestAttributes.filter(at => categoryAssignmentProperties.contains(at.categoryId))

    val factory = owlManager.getOWLDataFactory()

    val ontologyId = createOntologyId(sessionId, requestId)
    val ontology = owlManager.createOntology(IRI.create(ontologyId))

    otherOntologies.foreach(ontologyIRI => {
      val importDecl = factory.getOWLImportsDeclaration(ontologyIRI)
      owlManager.applyChange(new AddImport(ontology, importDecl))
      owlManager.loadOntology(ontologyIRI)
    })

    val categoryIndividualIds = getCategoryIndividualIds(ontologyId, requestId, requestAttributesOnlyCategories)

    val (categoryIndividuals, categoryAxioms) = axiomsFromCategories(factory, categoryIndividualIds)
    owlManager.addAxioms(ontology, categoryAxioms.toStream)

    val categoryAttributes = requestAttributesOnlyCategories.groupBy(at => at.categoryId)
    val attributeAxioms = categoryAttributes.flatMap { case (id, attributes) => axiomsFromAttributes(requestId, categoryIndividuals(id), attributes, categoryIndividuals, factory, ontology) }

    owlManager.addAxioms(ontology, createRequestAxioms(factory, ontologyId, requestId, categoryIndividuals))

    owlManager.addAxioms(ontology, attributeAxioms.toStream)

    owlManager.saveOntology(ontology, new SystemOutDocumentTarget())
    ontology
  }

  def updateOntology(owlManager: OWLOntologyManager)(existingOntology: OWLOntology, sessionId: Option[String], requestId: String, requestAttributes: Seq[ContextAttributeValue], otherOntologies: Set[IRI]): OWLOntology = {
    val requestAttributesOnlyCategories = requestAttributes.filter(at => categoryAssignmentProperties.contains(at.categoryId))

    val factory = owlManager.getOWLDataFactory()

    val ontologyId = createOntologyId(sessionId, requestId)

    val categoryIndividualIds = getCategoryIndividualIds(ontologyId, requestId, requestAttributesOnlyCategories)

    val (categoryIndividuals, categoryAxioms) = axiomsFromCategories(factory, categoryIndividualIds)
    owlManager.addAxioms(existingOntology, categoryAxioms.toStream)

    val categoryAttributes = requestAttributesOnlyCategories.groupBy(at => at.categoryId)
    val attributeAxioms = categoryAttributes.flatMap { case (id, attributes) => axiomsFromAttributes(requestId, categoryIndividuals(id), attributes, categoryIndividuals, factory, existingOntology) }

    owlManager.addAxioms(existingOntology, createRequestAxioms(factory, ontologyId, requestId, categoryIndividuals))

    owlManager.addAxioms(existingOntology, attributeAxioms.toStream)

    owlManager.saveOntology(existingOntology, new SystemOutDocumentTarget())
    existingOntology
  }

  private def getCategoryIndividualUri(ontologyId: String, requestId: String, attributes: Seq[ContextAttributeValue]): String = {
    val catId = attributes.head.categoryId

    val flatAttributes = (attributes.head match {
      case nested: NestedAttributeValue => nested.children
      case _: FlatAttributeValue => attributes
    }).filter(at => at.isInstanceOf[FlatAttributeValue]).map(at => at.asInstanceOf[FlatAttributeValue])

    flatAttributes.find(at => attributeDescribesId(at)).map(av => av.valueString)
      .getOrElse(getNewCategoryIndividualUri(ontologyId, requestId, catId.toString))
  }

  private def getNewCategoryIndividualUri(ontologyId: String, requestId: String, categoryId: String) = s"$ontologyId#${Constants.shortNames(categoryId)}_$requestId"

  private def getIndividualUri(requestId: String, elementId: URI, counter: Int): String = s"${elementId}_${counter}_${requestId}"

  private def isIRI(value: String): Boolean = new UrlValidator(UrlValidator.ALLOW_ALL_SCHEMES).isValid(value)

  private def attributeDescribesId(attributeValue: FlatAttributeValue): Boolean = {
    idAttributes.contains(attributeValue.attributeId.toString) && isIRI(attributeValue.valueString)
  }

  private def axiomsFromCategories(factory: OWLDataFactory, categoryIndividualIds: Map[URI, String]) = {
    val categoryAxioms = ListBuffer[OWLAxiom]()
    val categoryIndividuals = categoryIndividualIds.map {
      case (cat, id) => {
        val categoryIndividual = factory.getOWLNamedIndividual(IRI.create(id))
        val categoryClass = factory.getOWLClass(IRI.create(cat))
        val classAxiom = factory.getOWLClassAssertionAxiom(categoryClass, categoryIndividual)
        categoryAxioms += classAxiom

        (cat, categoryIndividual)
      }
    }

    (categoryIndividuals, categoryAxioms)
  }

  private def axiomsFromAttributes(requestId: String, parent: OWLNamedIndividual, attributes: Seq[ContextAttributeValue], categoryIndividuals: Map[URI, OWLNamedIndividual], factory: OWLDataFactory, ontology: OWLOntology): Seq[OWLIndividualAxiom] = {
    attributes.zipWithIndex.flatMap {
      case (attributeValue: FlatAttributeValue, i) =>
        if (attributeValue.attributeId == Constants.classIdForCategory(attributeValue.categoryId.toString)) {
          //TODO Check if this "if" is necessary. Not clear why we need to generate the class assertion.
          val elementClass = factory.getOWLClass(IRI.create(attributeValue.valueString))
          val classAxiom = factory.getOWLClassAssertionAxiom(elementClass, parent)

          Seq(classAxiom)
        } else if (ontology.containsObjectPropertyInSignature(IRI.create(attributeValue.attributeId), Imports.INCLUDED)) {
          val attribute = factory.getOWLObjectProperty(IRI.create(attributeValue.attributeId))

          val value = factory.getOWLNamedIndividual(IRI.create(attributeValue.valueString))

          Seq(factory.getOWLObjectPropertyAssertionAxiom(attribute, parent, value))
        }
        else {
          val attribute = factory.getOWLDataProperty(IRI.create(attributeValue.attributeId))
          val datatype = if (attributeValue.valueType.toString.startsWith("http://www.w3.org/2001/XMLSchema"))
            factory.getOWLDatatype(IRI.create(attributeValue.valueType))
          else
            factory.getOWLDatatype(IRI.create("http://www.w3.org/2001/XMLSchema#string"))

          val value = factory.getOWLLiteral(attributeValue.valueString, datatype)

          Seq(factory.getOWLDataPropertyAssertionAxiom(attribute, parent, value))
        }
      case (NestedAttributeValue(categoryId, propertyId, namespace, localName, children), i) =>
        val elementId = new URI(namespace + localName)
        val element = factory.getOWLNamedIndividual(IRI.create(getIndividualUri(requestId, elementId, i)))
        val elementClass = factory.getOWLClass(IRI.create(elementId))
        val classAxiom = factory.getOWLClassAssertionAxiom(elementClass, element)

        val propertyIdVal = propertyId.map(p => p.toString).getOrElse(namespace + "has" + localName.capitalize)
        val property = factory.getOWLObjectProperty(propertyIdVal)
        val propertyAxiom = factory.getOWLObjectPropertyAssertionAxiom(property, parent, element)
        Seq(classAxiom, propertyAxiom) ++ axiomsFromAttributes(requestId, element, children, categoryIndividuals, factory, ontology)
    }
  }

  private def createRequestAxioms(factory: OWLDataFactory, ontologyId: String, requestId: String, categoryIndividuals: Map[URI, OWLNamedIndividual]) = {
    val requestIndividual = factory.getOWLNamedIndividual(getRequestIndividualId(ontologyId, requestId))
    val requestClass = factory.getOWLClass(IRI.create(Constants.REQUEST_CLASS_ID))
    val requestAxiom = factory.getOWLClassAssertionAxiom(requestClass, requestIndividual)

    val categoryPropertyAxioms = categoryIndividuals.map({
      case (k, v) => {
        val propURI = categoryAssignmentProperties(k)
        val property = factory.getOWLObjectProperty(IRI.create(propURI))

        factory.getOWLObjectPropertyAssertionAxiom(property, requestIndividual, v)
      }
    }).toList

    categoryPropertyAxioms :+ requestAxiom
  }

}
