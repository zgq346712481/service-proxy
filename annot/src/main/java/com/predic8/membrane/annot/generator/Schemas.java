package com.predic8.membrane.annot.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.predic8.membrane.annot.ProcessingException;
import com.predic8.membrane.annot.model.AbstractJavadocedInfo;
import com.predic8.membrane.annot.model.AttributeInfo;
import com.predic8.membrane.annot.model.ChildElementInfo;
import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;
import com.predic8.membrane.annot.model.doc.Doc;
import com.predic8.membrane.annot.model.doc.Doc.Entry;

public class Schemas {
	
	private ProcessingEnvironment processingEnv;

	public Schemas(ProcessingEnvironment processingEnv) {
		this.processingEnv = processingEnv;
	}

	public void writeXSD(Model m) throws IOException {
		try {
			for (MainInfo main : m.getMains()) {
				List<Element> sources = new ArrayList<Element>();
				sources.add(main.getElement());
				sources.addAll(main.getInterceptorElements());

				FileObject o = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
						main.getAnnotation().outputPackage(), main.getAnnotation().outputName(), sources.toArray(new Element[0]));
				BufferedWriter bw = new BufferedWriter(o.openWriter());
				try {
					assembleXSD(bw, m, main);
				} finally {
					bw.close();
				}
			}
		} catch (FilerException e) {
			if (e.getMessage().contains("Source file already created"))
				return;
			throw e;
		}
	}

	private void assembleXSD(Writer w, Model m, MainInfo main) throws IOException, ProcessingException {
		String namespace = main.getAnnotation().targetNamespace();
		w.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
				"<xsd:schema xmlns=\"" + namespace + "\"\r\n" + 
				"	xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:beans=\"http://www.springframework.org/schema/beans\"\r\n" + 
				"	targetNamespace=\"" + namespace + "\"\r\n" + 
				"	elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">\r\n" + 
				"\r\n" + 
				"<!-- Automatically generated by " + Schemas.class.getName() + ". -->\r\n" + 
				"\r\n" + 
				"<xsd:import namespace=\"http://www.springframework.org/schema/beans\" schemaLocation=\"http://www.springframework.org/schema/beans/spring-beans-3.1.xsd\" />\r\n" + 
				"\r\n");
		assembleDeclarations(w, m, main);
		w.append("</xsd:schema>");
	}

	private void assembleDeclarations(Writer w, Model m, MainInfo main) throws ProcessingException, IOException {
		for (ElementInfo i : main.getElements().values())
			assembleElementDeclaration(w, m, main, i);
	}

	private void assembleElementDeclaration(Writer w, Model m, MainInfo main, ElementInfo i) throws ProcessingException, IOException {
		String footer;
		if (i.getAnnotation().topLevel()) {
			w.append("<xsd:element name=\""+ i.getAnnotation().name() +"\">\r\n");
			assembleDocumentation(w, i);
			w.append("<xsd:complexType>\r\n");
			footer = "</xsd:complexType>\r\n" + 
					"</xsd:element>\r\n";
		} else {
			w.append("<xsd:complexType name=\""+ i.getXSDTypeName(m) +"\">\r\n");
			footer = "</xsd:complexType>\r\n"; 
		}
		
		w.append("<xsd:complexContent " + (i.getAnnotation().mixed() ? "mixed=\"true\"" : "") + ">\r\n" + 
				"<xsd:extension base=\"beans:identifiedType\">\r\n");
		
		if (i.getAnnotation().xsd().length() == 0) {
			if (i.getAnnotation().mixed() && i.getCeis().size() > 0) {
				throw new ProcessingException(
						"@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
						i.getElement());
			}
			assembleElementInfo(w, m, main, i);
		} else {
			w.append(i.getAnnotation().xsd());
		}
		
		w.append("</xsd:extension>\r\n" + 
				"</xsd:complexContent>\r\n");
		w.append(footer);
	}

	private void assembleElementInfo(Writer w, Model m, MainInfo main, ElementInfo i) throws IOException {
		w.append("<xsd:sequence>\r\n");
		for (ChildElementInfo cei : i.getCeis()) {
			w.append("<xsd:choice" + (cei.isRequired() ? " minOccurs=\"1\"" : " minOccurs=\"0\"") + (cei.isList() ? " maxOccurs=\"unbounded\"" : "") + ">\r\n");
			assembleDocumentation(w, i);
			for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
				if (ei.getAnnotation().topLevel())
					w.append("<xsd:element ref=\"" + ei.getAnnotation().name() + "\">\r\n");
				else
					w.append("<xsd:element name=\"" + ei.getAnnotation().name() + "\" type=\"" + ei.getXSDTypeName(m) + "\">\r\n");
				assembleDocumentation(w, ei);
				w.append("</xsd:element>\r\n");
			}
			if (cei.getAnnotation().allowForeign())
				w.append("<xsd:any namespace=\"##other\" processContents=\"strict\" />\r\n");
			w.append("</xsd:choice>\r\n");
		}
		w.append("</xsd:sequence>\r\n");
		for (AttributeInfo ai : i.getAis())
			if (!ai.getXMLName().equals("id"))
				assembleAttributeDeclaration(w, ai);
		if (i.getOai() != null) {
			w.append("<xsd:anyAttribute processContents=\"skip\">\r\n");
			assembleDocumentation(w, i.getOai());
			w.append("</xsd:anyAttribute>\r\n");
		}
	}

	private void assembleAttributeDeclaration(Writer w, AttributeInfo ai) throws IOException {
		// TODO: default value
		w.append("<xsd:attribute name=\"" + ai.getXMLName() + "\" type=\"" + ai.getXSDType(processingEnv.getTypeUtils()) + "\" "
				+ (ai.isRequired() ? "use=\"required\"" : "") + ">\r\n");
		assembleDocumentation(w, ai);
		w.append("</xsd:attribute>\r\n");
	}
	
	private void assembleDocumentation(Writer w, AbstractJavadocedInfo aji) throws IOException {
		Doc doc = aji.getDoc(processingEnv);
		if (doc == null)
			return;
		w.append("<xsd:annotation>\r\n");
		w.append("<xsd:documentation>");
		for (Entry e : doc.getEntries()) {
			w.append(xmlEscape("<h3><b>"));
			w.append(xmlEscape(capitalize(e.getKey()) + ":"));
			w.append(xmlEscape("</b></h3> "));
			w.append(xmlEscape(e.getValueAsXMLSnippet(false)));
			w.append(xmlEscape("<br/>"));
		}
		w.append("</xsd:documentation>\r\n");
		w.append("</xsd:annotation>\r\n");
	}

	private CharSequence xmlEscape(String string) {
		return string.replace("<", "&lt;").replace(">", "&gt;");
	}

	private String capitalize(String key) {
		if (key.length() == 0)
			return key;
		return Character.toUpperCase(key.charAt(0)) + key.substring(1);
	}

}
