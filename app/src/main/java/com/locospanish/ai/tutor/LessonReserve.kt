package com.locospanish.ai.tutor

/** Emergency material only: generation remains the primary source after the initial vocabulary. */
object LessonReserve {
    private val material = mapOf(
        "daily" to "despertarse=budzić się;levantarse=wstawać;vestirse=ubierać się;lavarse=myć się;desayunar=jeść śniadanie;caminar=chodzić;leer=czytać;escribir=pisać;escuchar=słuchać;cocinar=gotować;descansar=odpoczywać;limpiar=sprzątać;abrir=otwierać;cerrar=zamykać;buscar=szukać;encontrar=znajdować;volver=wracać;salir=wychodzić;llegar=przyjeżdżać;esperar=czekać",
        "hotel" to "el balcón=balkon;la manta=koc;la sábana=prześcieradło;el armario=szafa;la percha=wieszak;el jabón=mydło;el champú=szampon;el espejo=lustro;la piscina=basen;el gimnasio=siłownia;el aparcamiento=parking;la escalera=schody",
        "bar" to "la copa=kieliszek;el cóctel=koktajl;el refresco=napój gazowany;la aceituna=oliwka;el limón=cytryna;la naranja=pomarańcza;la tapa=przekąska;la terraza=taras;la música=muzyka;pedir=zamawiać;brindar=wznosić toast;beber=pić",
        "restaurant" to "el arroz=ryż;la patata=ziemniak;el pollo=kurczak;la ensalada=sałatka;el postre=deser;el aceite=olej;el vinagre=ocet;la pimienta=pieprz;el ajo=czosnek;la cebolla=cebula;el tomate=pomidor;la verdura=warzywo",
        "cafe" to "la galleta=ciastko;el chocolate=czekolada;la miel=miód;la canela=cynamon;la espuma=pianka;el bizcocho=ciasto biszkoptowe;el zumo=sok;la tostada=tost;la mantequilla=masło;la mermelada=dżem;caliente=gorący;frío=zimny",
        "shopping" to "el pantalón=spodnie;la falda=spódnica;el vestido=sukienka;el abrigo=płaszcz;el calcetín=skarpetka;el cinturón=pasek;el probador=przymierzalnia;la caja=kasa;la etiqueta=metka;el escaparate=witryna;caro=drogi;barato=tani",
        "airport" to "el aeropuerto=lotnisko;el avión=samolot;la terminal=terminal;la escala=przesiadka;el piloto=pilot;la azafata=stewardesa;la aduana=odprawa celna;la cinta=taśma;el control=kontrola;despegar=startować;aterrizar=lądować;facturar=nadawać bagaż",
        "taxi" to "el tráfico=ruch uliczny;el semáforo=sygnalizator;la avenida=aleja;el cruce=skrzyżowanie;el puente=most;la estación=dworzec;el aeropuerto=lotnisko;el taxímetro=taksometr;el cambio=reszta;girar=skręcać;recto=prosto;lejos=daleko",
        "people" to "la madre=matka;el padre=ojciec;el hijo=syn;la hija=córka;el abuelo=dziadek;la abuela=babcia;el primo=kuzyn;la prima=kuzynka;el vecino=sąsiad;la vecina=sąsiadka;joven=młody;simpático=sympatyczny",
        "date" to "la cena=kolacja;el regalo=prezent;la pareja=para;el amor=miłość;el abrazo=uścisk;la playa=plaża;el restaurante=restauracja;el concierto=koncert;bailar=tańczyć;invitar=zapraszać;reír=śmiać się;gustar=podobać się",
        "rent" to "el dormitorio=sypialnia;el salón=salon;el baño=łazienka;el balcón=balkon;el ascensor=winda;el tejado=dach;la pared=ściana;el suelo=podłoga;la calefacción=ogrzewanie;la nevera=lodówka;la lavadora=pralka;alquilar=wynajmować",
        "interview" to "la formación=wykształcenie;el título=dyplom;el curso=kurs;el idioma=język;el logro=osiągnięcie;el proyecto=projekt;la responsabilidad=odpowiedzialność;la práctica=praktyka;la motivación=motywacja;organizar=organizować;resolver=rozwiązywać;colaborar=współpracować",
        "employer" to "el candidato=kandydat;la candidata=kandydatka;la jornada=dzień pracy;el descanso=odpoczynek;la nómina=lista płac;el permiso=pozwolenie;el contrato=umowa;la incorporación=rozpoczęcie pracy;contratar=zatrudniać;confirmar=potwierdzać;enviar=wysyłać;llamar=dzwonić",
        "hotelwork" to "la limpieza=sprzątanie;la lavandería=pralnia;la almohada=poduszka;la sábana=prześcieradło;la bandeja=taca;el carrito=wózek;el uniforme=mundur;el almacén=magazyn;reponer=uzupełniać;avisar=powiadamiać;entregar=dostarczać;recoger=zbierać",
        "maintenance" to "el destornillador=śrubokręt;la tuerca=nakrętka;el alicate=szczypce;la tubería=rura;el grifo=kran;la escalera=drabina;la bombilla=żarówka;el guante=rękawica;apretar=dokręcać;cortar=ciąć;cambiar=wymieniać;comprobar=sprawdzać",
        "electronics" to "el condensador=kondensator;el transistor=tranzystor;el interruptor=przełącznik;el conector=złącze;la soldadura=lutowanie;el multímetro=multimetr;el osciloscopio=oscyloskop;la señal=sygnał;la frecuencia=częstotliwość;soldar=lutować;conectar=podłączać;desconectar=odłączać",
        "emergency" to "el hospital=szpital;la farmacia=apteka;el bombero=strażak;la enfermera=pielęgniarka;la fiebre=gorączka;el golpe=uderzenie;la caída=upadek;la salida=wyjście;respirar=oddychać;socorrer=ratować;quemar=parzyć;sangrar=krwawić",
        "free" to "aprender=uczyć się;entender=rozumieć;recordar=pamiętać;olvidar=zapominać;mirar=patrzeć;pensar=myśleć;querer=chcieć;poder=móc;necesitar=potrzebować;preguntar=pytać;responder=odpowiadać;explicar=wyjaśniać"
    )
    private val continuation=mapOf("bar" to "la pajita=słomka;el posavasos=podkładka pod szklankę;la jarra=dzbanek;la caña=małe piwo z beczki;el chupito=kieliszek mocnego alkoholu;la propina=napiwek;el taburete=stołek barowy;la ronda=kolejka napojów;la sidra=cydr;el vermut=wermut;la ginebra=gin;el ron=rum;el whisky=whisky;el tequila=tequila;el cacahuete=orzeszek ziemny;la almendra=migdał;el barril=beczka;la bodega=winiarnia;el aperitivo=aperitif;la resaca=kac;la sed=pragnienie;la mezcla=mieszanka;el sorbo=łyk;la gota=kropla;el cubito=kostka lodu;el abridor=otwieracz;el sacacorchos=korkociąg;la cucharilla=łyżeczka;la coctelera=shaker;la camarera=kelnerka;el dueño=właściciel;la dueña=właścicielka;reservado=zarezerwowany;libre=wolny;lleno=pełny;vacío=pusty;fresco=chłodny;seco=wytrawny;dulce=słodki;amargo=gorzki;suave=łagodny;fuerte=mocny;mezclar=mieszać;servir=podawać;llenar=napełniać;cobrar=pobierać opłatę")
    fun words(scenario: String): List<LessonWord> = (material[scenario].orEmpty()+";"+continuation[scenario].orEmpty()).split(';').filter { '=' in it }.map {
        val pair=it.split('=',limit=2); LessonWord(pair[0],pair[1])
    }
    fun next(scenario:String,excluded:Collection<String>):LessonWord? {
        val blocked=excluded.map(AssessmentPolicy::key).toSet()
        return words(scenario).firstOrNull { AssessmentPolicy.key(it.es) !in blocked }
    }
}
