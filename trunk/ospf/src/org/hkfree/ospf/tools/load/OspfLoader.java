package org.hkfree.ospf.tools.load;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hkfree.ospf.model.linkfault.LinkFaultModel;
import org.hkfree.ospf.model.ospf.Link;
import org.hkfree.ospf.model.ospf.OspfModel;
import org.hkfree.ospf.model.ospf.Router;
import org.hkfree.ospf.model.ospf.StubLink;
import org.hkfree.ospf.tools.geo.GeoCoordinatesTransformator;

/**
 * Třída, která slouží k načítání OspfModelu z externích souborů.
 * Načítání může probíhat dle nastavení z místního adresáře, nebo z webu.
 * @author Jakub Menzel
 * @author Jan Schovánek
 */
public class OspfLoader {

    /**
     * Konstruktor - vytvoří instanci třídy
     */
    public OspfLoader() {}


    /**
     * Metoda, která načte ze zadaného umístění topologii sítě routerů
     * @throws IOException
     */
    public static void loadTopology(OspfModel model, BufferedReader input) throws IOException {
	BufferedReader vstup = null;
	String radek = "";
	Pattern ipPattern = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
	Matcher ipMatcher = null;
	Pattern maskPattern = Pattern.compile("^.*/([0-9]{1,2})");
	Matcher maskMatcher = null;
	vstup = input;
	while ((radek = vstup.readLine()) != null) {
	    if (radek.contains("Link State ID")) {
		String linkName = "";
		int linkMask = 0;
		ipMatcher = ipPattern.matcher(radek);
		ipMatcher.find();
		linkName = ipMatcher.group(0);
		while (!((radek = vstup.readLine()).contains("Network Mask"))) {}
		maskMatcher = maskPattern.matcher(radek);
		maskMatcher.find();
		linkMask = Integer.valueOf(maskMatcher.group(1));
		model.addOspfLink(linkName, linkMask);
		// čtení řádků než narazí na Attached Router
		while (!((radek = vstup.readLine()).contains("Attached Router"))) {}
		// načtení první IP jdoucí do spoje
		ipMatcher = ipPattern.matcher(radek);
		ipMatcher.find();
		model.addRouter(ipMatcher.group(0));
		// načtení zbylých IP jdoucích do spoje
		while ((radek = vstup.readLine()).contains("Attached Router")) {
		    ipMatcher = ipPattern.matcher(radek);
		    ipMatcher.find();
		    model.addRouter(ipMatcher.group(0));
		}
	    }
	}
    }





    /**
     * Metoda, která načte ze zadaného umístění ceny spojů načtené topologie
     * @throws IOException
     */
    public static void loadCosts(OspfModel model, String routerIP, BufferedReader input) throws IOException {
	BufferedReader infoUzlu = null;
	Router router = null;
	String radek;
	Pattern costPattern = Pattern.compile("^.*:\\s([0-9]{1,})");
	Matcher costMatcher = null;
	Pattern ipPattern = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
	Matcher ipMatcher = null;
	int cena;
	List<Link> act_spoje = new ArrayList<Link>();
	router = model.getRouterByIp(routerIP);
	if (router != null) {
	    for (Link s : model.getLinks()) {
		if (s.containsRouter(router))
		    act_spoje.add(s);
	    }
	    infoUzlu = input;
	    // změnit cenu a ip interface routeru ve spojích kde router figuruje
	    while ((radek = infoUzlu.readLine()) != null) {
		for (Link s : act_spoje) {
		    if (radek.contains("Link ID") && radek.endsWith(s.getLinkID())) {
			String interfaceIp = "";
			while (!(radek = infoUzlu.readLine()).contains("Interface")) {}
			ipMatcher = ipPattern.matcher(radek);
			ipMatcher.find();
			interfaceIp = ipMatcher.group(0);
			while (!(radek = infoUzlu.readLine()).contains("TOS 0 Metric")) {}
			costMatcher = costPattern.matcher(radek);
			costMatcher.find();
			cena = Integer.valueOf(costMatcher.group(1));
			model.updateCost(s.getLinkID(), router, interfaceIp, cena);
		    } else if (radek.contains("Stub Network")) {
			// nacitani stub spoje
			StubLink stub = new StubLink();
			while (!(radek = infoUzlu.readLine()).contains("(Link ID) Net")) {}
			ipMatcher = ipPattern.matcher(radek);
			ipMatcher.find();
			stub.setLinkID(ipMatcher.group(0));
			while (!(radek = infoUzlu.readLine()).contains("(Link Data) Network Mask")) {}
			ipMatcher = ipPattern.matcher(radek);
			ipMatcher.find();
			stub.setMask(ipMatcher.group(0));
			while (!(radek = infoUzlu.readLine()).contains("TOS 0 Metric")) {}
			costMatcher = costPattern.matcher(radek);
			costMatcher.find();
			stub.setCost(Integer.valueOf(costMatcher.group(1)));
			router.getStubs().add(stub);
		    }
		}
	    }
	    for (int i = act_spoje.size() - 1; i >= 0; i--) {
		act_spoje.remove(i);
	    }
	}
    }

    /**
     * Metoda, která načte ze zadaného umístění jména routerů a náležitě upraví model
     * @throws IOException
     */
    public static void loadRouterNames(OspfModel model, BufferedReader input) throws IOException {
	BufferedReader vstup = null;
	String radek = "", ip = "", name = "";
	Pattern namePattern = Pattern.compile("^([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\\s+(.+)$");
	Matcher nameMatcher = null;
	vstup = input;
	while ((radek = vstup.readLine()) != null) {
	    nameMatcher = namePattern.matcher(radek);
	    nameMatcher.find();
	    if (nameMatcher.matches()) {
		ip = nameMatcher.group(1);
		name = nameMatcher.group(2);
		for (Router r : model.getRouters()) {
		    if (r.getId().equals(ip) && !ip.equals(name))
			r.setName(name);
		}
	    }
	}
    }


    /**
     * Metoda, která načte ze zadaného umístění logy o výpadcích
     * @param model
     * @param input
     * @throws IOException
     */
    public static void loadOSPFLog(LinkFaultModel model, BufferedReader input) throws Exception {
	BufferedReader vstup = null;
	SimpleDateFormat inputDateFormater = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	Pattern logPattern = Pattern
		.compile("^([0-9]{4}/[0-9]{2}/[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})\\s+.+id\\((.+)\\).+ar.+$");
	Matcher logMatcher = null;
	vstup = input;
	String line = "";
	while ((line = vstup.readLine()) != null) {
	    logMatcher = logPattern.matcher(line);
	    logMatcher.find();
	    if (logMatcher.matches()) {
		model.addLinkFault(inputDateFormater.parse(logMatcher.group(1)), logMatcher.group(2));
	    }
	}
    }


    /**
     * Metoda, která načte ze zadaného umístění pozice routerů
     * @param model
     * @param input
     * @throws Exception
     */
    public static void loadRouterGeoPositions(OspfModel model, BufferedReader input) throws Exception {
	BufferedReader vstup = null;
	Pattern geoPattern = Pattern
		.compile("^([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\\s+([0-9]+)\\s+([0-9]+)(.*)$");
	Matcher geoMatcher = null;
	vstup = input;
	GeoCoordinatesTransformator geoCoorTransormator = new GeoCoordinatesTransformator();
	String line = "";
	while ((line = vstup.readLine()) != null) {
	    geoMatcher = geoPattern.matcher(line);
	    geoMatcher.find();
	    if (geoMatcher.matches()) {
		Router r = model.getRouterByIp(geoMatcher.group(1));
		if (r != null) {
		    r.setGpsPosition(geoCoorTransormator.transformJTSKToWGS(Integer.valueOf(geoMatcher.group(2)),
			    Integer.valueOf(geoMatcher.group(3))));
		}
	    }
	}
    }


    public static void getTopologyFromData(OspfModel model, BufferedReader input) throws NumberFormatException, IOException {
	String linkStateId = null;
	String linkId = null;
	String linkData = null;
	int cost;
	int numberOfLinks;
	String radek = "";
	Pattern ipPattern = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
	Matcher ipMatcher = null;
	Pattern maskPattern = Pattern.compile("^.*/([0-9]{1,2})");
	Matcher maskMatcher = null;
	Pattern costPattern = Pattern.compile("^.*:\\s([0-9]{1,})");
	Matcher costMatcher = null;
	// prikazy dle poradi vyskytu v datech
	String cmd1 = "show ip ospf database network";
	String cmd2 = "show ip ospf database router";
	String cmd3 = "show ipv6 ospf6 database router detail";
	boolean isStub = false;
	int cmd = 0;
	while ((radek = input.readLine()) != null) {
	    if (radek.contains(cmd1))
		cmd = 1;
	    if (radek.contains(cmd2))
		cmd = 2;
	    if (radek.contains(cmd3))
		cmd = 3;
	    switch (cmd) {
		case 1:
		    if (radek.contains("Link State ID")) {
			String linkName = "";
			int linkMask = 0;
			ipMatcher = ipPattern.matcher(radek);
			ipMatcher.find();
			linkName = ipMatcher.group(0);
			while (!((radek = input.readLine()).contains("Network Mask"))) {}
			maskMatcher = maskPattern.matcher(radek);
			maskMatcher.find();
			linkMask = Integer.valueOf(maskMatcher.group(1));
			model.addOspfLink(linkName, linkMask);
			// čtení řádků než narazí na Attached Router
			while (!((radek = input.readLine()).contains("Attached Router"))) {}
			// načtení první IP jdoucí do spoje
			ipMatcher = ipPattern.matcher(radek);
			ipMatcher.find();
			model.addRouter(ipMatcher.group(0));
			// načtení zbylých IP jdoucích do spoje
			while ((radek = input.readLine()).contains("Attached Router")) {
			    ipMatcher = ipPattern.matcher(radek);
			    ipMatcher.find();
			    model.addRouter(ipMatcher.group(0));
			}
		    }
		    break;
		case 2:
		    if (radek.contains("Link State ID")) {
			ipMatcher = ipPattern.matcher(radek);
			ipMatcher.find();
			linkStateId = ipMatcher.group(0);
			while (!(radek = input.readLine()).contains("Number of Links")) {}
			costMatcher = costPattern.matcher(radek);
			costMatcher.find();
			numberOfLinks = Integer.valueOf(costMatcher.group(1));
			for (int i = 0; i < numberOfLinks; i++) {
			    while (!(radek = input.readLine()).contains("Link connected to")) {}
			    isStub = radek.endsWith("Stub Network");
			    while (!(radek = input.readLine()).contains("(Link ID)")) {}
			    ipMatcher = ipPattern.matcher(radek);
			    ipMatcher.find();
			    linkId = ipMatcher.group(0);
			    while (!(radek = input.readLine()).contains("(Link Data)")) {}
			    ipMatcher = ipPattern.matcher(radek);
			    ipMatcher.find();
			    linkData = ipMatcher.group(0);
			    while (!(radek = input.readLine()).contains("TOS 0 Metric")) {}
			    costMatcher = costPattern.matcher(radek);
			    costMatcher.find();
			    cost = Integer.valueOf(costMatcher.group(1));
			    if (isStub) {
				model.addStubNetwork(linkStateId, linkId, linkData, cost);
			    } else {
				model.updateCost(linkId, linkStateId, linkData, cost);
			    }
			}
		    }
		    break;
		case 3:
		    //TODO nacist 
		    break;
	    }
	}
    }
    

//    public void loadTopologyIPv6(OspfModel model, BufferedReader input) throws IOException {
//   	BufferedReader vstup = null;
//   	// Reader in = null;
//   	String radek = "";
//   	Pattern ipPattern = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
//   	Matcher ipMatcher = null;
////   	Pattern maskPattern = Pattern.compile("^.*/([0-9]{1,2})");
////   	Matcher maskMatcher = null;
//   	vstup = input;
//   	while ((radek = vstup.readLine()) != null) {
//   	    if (radek.contains("Link State ID")) {
//   		String linkName = "";
//   		int linkMask = 0;
//   		ipMatcher = ipPattern.matcher(radek);
//   		ipMatcher.find();
//   		linkName = ipMatcher.group(0);
//   		// while (!((radek = vstup.readLine()).contains("Network Mask"))) {}
//   		// maskMatcher = maskPattern.matcher(radek);
//   		// maskMatcher.find();
//   		// linkMask = Integer.valueOf(maskMatcher.group(1));
//   		model.addOspfLink(linkName, linkMask);
//   		// čtení řádků než narazí na Attached Router
//   		while (!((radek = vstup.readLine()).contains("Attached Router"))) {}
//   		// načtení první IP jdoucí do spoje
//   		ipMatcher = ipPattern.matcher(radek);
//   		ipMatcher.find();
//   		model.addRouter(ipMatcher.group(0));
//   		// načtení zbylých IP jdoucích do spoje
//   		while ((radek = vstup.readLine()).contains("Attached Router")) {
//   		    ipMatcher = ipPattern.matcher(radek);
//   		    ipMatcher.find();
//   		    model.addRouter(ipMatcher.group(0));
//   		}
//   	    }
//   	}
//       }
//
//   public void loadCostsIPv6(OspfModel model, String routerIP, BufferedReader input) throws IOException {
//	BufferedReader infoUzlu = null;
//	Router router = null;
//	String radek;
//	Pattern costPattern = Pattern.compile("^.*:\\s([0-9]{1,})");
//	Matcher costMatcher = null;
//	Pattern ipPattern = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
//	Matcher ipMatcher = null;
//	int cost;
//	List<OspfLink> act_spoje = new ArrayList<OspfLink>();
//	router = model.getRouterByIp(routerIP);
//	if (router != null) {
//	    for (OspfLink s : model.getOspfLinks()) {
//		if (s.containsRouter(router))
//		    act_spoje.add(s);
//	    }
//	    
//	    //Link ID radek.endsWith(s.getLinkID())
//	    //interfaceIp  (Link Data) Router Interface address: 10.107.0.107 
//	    
//	    infoUzlu = input;
//	    // změnit cenu a ip interface routeru ve spojích kde router figuruje
//	    while ((radek = infoUzlu.readLine()) != null) {
//		if (radek.contains("Transit-Network Metric")) {
//		    // nasel jsem zaznam, nactu jeho data
//		    costMatcher = costPattern.matcher(radek);
//		    costMatcher.find();
//		    cost = Integer.valueOf(costMatcher.group(1));
//		    while (!(radek = infoUzlu.readLine()).contains("Neighbor Interface ID")) {}
//		    ipMatcher = ipPattern.matcher(radek);
//		    ipMatcher.find();
//		    String neighborInterface = ipMatcher.group(0);
//		    while (!(radek = infoUzlu.readLine()).contains("Neighbor Router ID")) {}
//		    ipMatcher = ipPattern.matcher(radek);
//		    ipMatcher.find();
////		    String neighborRouter = ipMatcher.group(0);
//		    for (OspfLink s : act_spoje) {
//			if (neighborInterface.endsWith(s.getLinkID())) {
//			    model.updateCost(s.getLinkID(), router, neighborInterface, cost);
//			}
//		    }
//		}
//	    }
//	    for (int i = act_spoje.size() - 1; i >= 0; i--) {
//		act_spoje.remove(i);
//	    }
//	}
//    }

}