package com.locospanish.ai.tutor

/** A noun's teaching form is shared by the card, native voice and grading target. */
object TeachingForm {
 private val articles=setOf("el","la","los","las","un","una","unos","unas")
 private val catalog by lazy {
  (TopicLessons.plans.keys.flatMap { TopicLessons.vocabulary(it)+LessonReserve.words(it) })
   .filter { it.es.substringBefore(' ').lowercase() in articles }
   .associateBy { AssessmentPolicy.key(it.es) }
 }
 // Bank nouns that were stored bare: every noun is shown and spoken with its article, never sometimes.
 private val nouns by lazy { "la cama;la almohada;la toalla;el ascensor;el desayuno;la salida;la entrada;la maleta;la ducha;el baño;el vino;el zumo;el vaso;el hielo;la botella;el camarero;la barra;la comida;la carne;el pescado;la sopa;el pan;la sal;el tenedor;el cuchillo;la leche;el azúcar;la taza;el té;el pastel;la cuchara;la servilleta;la tienda;la camisa;los zapatos;la bolsa;el dinero;el descuento;el avión;la llegada;el asiento;el embarque;la calle;el coche;el conductor;la izquierda;la derecha;la edad;el país;la ciudad;la familia;el hermano;la hermana;el beso;la flor;la sonrisa;el parque;la cocina;la ventana;la puerta;el vecino;el mes;los muebles;la empresa;el currículum;el jefe;el empleo;el teléfono;el salario;la vacante;el correo;el turno;el equipaje;el tornillo;el martillo;el cable;el enchufe;el agua;la fuga;la batería;la placa;el diodo;el fusible;el voltio".split(';').associateBy { AssessmentPolicy.key(it) } }
 // Unknown words still get the article their ending makes certain.
 private fun byEnding(text:String)=Regex("(?:ción|sión|dad|tad|tud|umbre)$").containsMatchIn(text.lowercase()).takeIf { it }?.let { "la $text" }
 fun complete(word:LessonWord,article:String=""):LessonWord {
  val text=word.es.trim().replace(Regex("\\s+")," ")
  if(!LessonTurnPolicy.singleWord(text) || text.substringBefore(' ').lowercase() in articles) return word.copy(es=text)
  val prefix=article.trim().lowercase().takeIf { it in articles }
  val full=catalog[AssessmentPolicy.key(text)]?.es ?: nouns[AssessmentPolicy.key(text)] ?: prefix?.let { "$it $text" } ?: byEnding(text) ?: text
  return word.copy(es=full)
 }
 fun refine(current:LessonWord?,proposed:LessonWord):LessonWord? {
  if(current==null || AssessmentPolicy.key(current.es)!=AssessmentPolicy.key(proposed.es)) return current
  if(current.es.substringBefore(' ').lowercase() in articles) return current
  if(proposed.es.substringBefore(' ').lowercase() !in articles) return current
  return proposed.copy(pl=current.pl)
 }
}
