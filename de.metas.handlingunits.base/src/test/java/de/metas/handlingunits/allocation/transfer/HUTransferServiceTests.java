package de.metas.handlingunits.allocation.transfer;

import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.adempiere.util.Services;
import org.compiere.model.I_C_BPartner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.w3c.dom.Node;

import de.metas.adempiere.model.I_C_BPartner_Location;
import de.metas.handlingunits.HUXmlConverter;
import de.metas.handlingunits.IHandlingUnitsBL;
import de.metas.handlingunits.IHandlingUnitsDAO;
import de.metas.handlingunits.allocation.impl.HUProducerDestination;
import de.metas.handlingunits.allocation.transfer.impl.LUTUProducerDestination;
import de.metas.handlingunits.allocation.transfer.impl.LUTUProducerDestinationTestSupport;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_HU_PI_Item_Product;
import de.metas.handlingunits.model.I_M_Locator;
import de.metas.interfaces.I_M_Warehouse;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
@RunWith(Theories.class)
public class HUTransferServiceTests
{
	/**
	 * This dataPoint shall enable us to test with both values of {@code isOwnPackingMaterials}.
	 */
	@DataPoints("isOwnPackingMaterials")
	public static boolean[] isOwnPackingMaterials = { true, false };

	@DataPoints("isAggregateCU")
	public static boolean[] isAggregateCU = { true, false };

	private LUTUProducerDestinationTestSupport data;

	private IHandlingUnitsDAO handlingUnitsDAO;
	private IHandlingUnitsBL handlingUnitsBL;

	@Before
	public void init()
	{
		data = new LUTUProducerDestinationTestSupport();
		handlingUnitsDAO = Services.get(IHandlingUnitsDAO.class);
		handlingUnitsBL = Services.get(IHandlingUnitsBL.class);
	}

	/**
	 * Tests {@link HUTransferService#splitCU_To_NewCU(I_M_HU, org.compiere.model.I_M_Product, org.compiere.model.I_C_UOM, BigDecimal)}
	 * and verifies that the method does nothing if the given CU has no parent and if the given qty is equal or greater than the CU's full quantity.
	 */
	@Test
	public void testSplitCU_To_NewCU_MaxValueNoParent()
	{
		final I_M_HU cuToSplit = mkRealStandAloneCUToSplit("3");
		assertThat(cuToSplit.getM_HU_Item_Parent(), nullValue()); // this test makes no sense if the given CU has a parent

		// invoke the method under test
		final List<I_M_HU> newCUs = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewCU(cuToSplit, data.helper.pTomato, data.helper.uomKg, new BigDecimal("3"));
		assertThat(newCUs.size(), is(0));
	}

	/**
	 * Tests {@link HUTransferService#splitCU_To_NewCU(I_M_HU, org.compiere.model.I_M_Product, org.compiere.model.I_C_UOM, BigDecimal)}
	 * and verifies that the method removes the given CU from its parent, if it has a parent and if the given qty is equal or greater than the CU's full quantity.
	 */
	@Test
	public void testSplitCU_To_NewCU_MaxValueParent()
	{
		final I_M_HU cuToSplit = mkRealCUWithTUToSplit("3");
		final I_M_HU parentTU = cuToSplit.getM_HU_Item_Parent().getM_HU();

		// invoke the method under test
		final List<I_M_HU> newCUs = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewCU(cuToSplit, data.helper.pTomato, data.helper.uomKg, new BigDecimal("3"));

		assertThat(newCUs.size(), is(1));
		assertThat(newCUs.get(0).getM_HU_ID(), is(cuToSplit.getM_HU_ID()));
		assertThat(handlingUnitsDAO.retrieveIncludedHUs(parentTU).isEmpty(), is(true));
		assertThat(cuToSplit.getM_HU_Item_Parent(), nullValue());
	}

	/**
	 * Tests {@link HUTransferService#splitCU_To_NewCU(I_M_HU, org.compiere.model.I_M_Product, org.compiere.model.I_C_UOM, BigDecimal)} by splitting one tomato onto a new CU.
	 * Also verifies that the new CU has the same C_BPartner, M_Locator etc as the old CU.
	 */
	@Test
	public void testSplitCU_To_NewCU_1Tomato()
	{
		final IHandlingUnitsDAO handlingUnitsDAO = Services.get(IHandlingUnitsDAO.class);

		final I_C_BPartner bpartner = data.helper.createBPartner("testVendor");
		final I_C_BPartner_Location bPartnerLocation = data.helper.createBPartnerLocation(bpartner);

		final I_M_Warehouse warehouse = data.helper.createWarehouse("testWarehouse");
		final I_M_Locator locator = data.helper.createLocator("testLocator", warehouse);

		final I_M_HU sourceTU;
		final I_M_HU cuToSplit;
		{
			final LUTUProducerDestination lutuProducer = new LUTUProducerDestination();

			lutuProducer.setNoLU();
			lutuProducer.setTUPI(data.piTU_IFCO);
			lutuProducer.setC_BPartner(bpartner);
			lutuProducer.setM_Locator(locator);
			lutuProducer.setC_BPartner_Location_ID(bPartnerLocation.getC_BPartner_Location_ID());

			data.helper.load(lutuProducer, data.helper.pTomato, new BigDecimal("2"), data.helper.uomKg);
			final List<I_M_HU> createdTUs = lutuProducer.getCreatedHUs();

			assertThat(createdTUs.size(), is(1));
			sourceTU = createdTUs.get(0);

			final List<I_M_HU> createdCUs = handlingUnitsDAO.retrieveIncludedHUs(createdTUs.get(0));
			assertThat(createdCUs.size(), is(1));

			cuToSplit = createdCUs.get(0);
		}

		// invoke the method under test
		final List<I_M_HU> newCUs = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewCU(cuToSplit, data.helper.pTomato, data.helper.uomKg, BigDecimal.ONE);

		assertThat(newCUs.size(), is(1));

		final Node sourceTUXML = HUXmlConverter.toXml(sourceTU);
		assertThat(sourceTUXML, hasXPath("count(HU-TU_IFCO[@HUStatus='P'])", is("1")));
		assertThat(sourceTUXML, hasXPath("count(HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @Qty='1.000' and @C_UOM_Name='Kg'])", is("1")));

		final Node cuToSplitXML = HUXmlConverter.toXml(cuToSplit);
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI[@HUStatus='P'])", is("1")));
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @Qty='1.000' and @C_UOM_Name='Kg'])", is("1")));

		final Node newCUXML = HUXmlConverter.toXml(newCUs.get(0));
		assertThat(newCUXML, not(hasXPath("HU-VirtualPI/M_HU_Item_Parent_ID"))); // verify that there is no parent HU
		assertThat(newCUXML, hasXPath("count(HU-VirtualPI[@HUStatus='P'])", is("1")));
		assertThat(newCUXML, hasXPath("count(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @Qty='1.000' and @C_UOM_Name='Kg'])", is("1")));
		assertThat(newCUXML, hasXPath("string(HU-VirtualPI/@C_BPartner_ID)", is(Integer.toString(bpartner.getC_BPartner_ID())))); // verify that the bpartner is propagated
		assertThat(newCUXML, hasXPath("string(HU-VirtualPI/@C_BPartner_Location_ID)", is(Integer.toString(bPartnerLocation.getC_BPartner_Location_ID())))); // verify that the bpartner location is propagated
		assertThat(newCUXML, hasXPath("string(HU-VirtualPI/@M_Locator_ID)", is(Integer.toString(locator.getM_Locator_ID())))); // verify that the locator is propagated
	}

	@Theory
	public void testSplitRealCU_To_NewTUs_1Tomato_TU_Capacity_2(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		final I_M_HU cuToSplit = mkRealStandAloneCUToSplit("40");

		// invoke the method under test
		final List<I_M_HU> newTUs = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewTUs(cuToSplit, data.helper.pTomato, data.helper.uomKg, BigDecimal.ONE, data.piTU_Item_Product_Bag_8KgTomatoes, isOwnPackingMaterials);

		assertThat(newTUs.size(), is(1));

		// data.helper.commitAndDumpHU(cuToSplit);

		final Node cuToSplitXML = HUXmlConverter.toXml(cuToSplit);
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI[@HUStatus='P'])", is("1")));
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @Qty='39.000' and @C_UOM_Name='Kg'])", is("1")));

		final Node newTUXML = HUXmlConverter.toXml(newTUs.get(0));

		assertThat(newTUXML, hasXPath("count(HU-TU_Bag[@HUStatus='P'])", is("1")));
		assertThat(newTUXML, hasXPath("string(HU-TU_Bag/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));
		assertThat(newTUXML, hasXPath("count(HU-TU_Bag/Storage[@M_Product_Value='Tomato' and @Qty='1.000' and @C_UOM_Name='Kg'])", is("1")));
	}

	/**
	 * Tests {@link HUTransferService#splitCU_To_NewTUs(I_M_HU, org.compiere.model.I_M_Product, org.compiere.model.I_C_UOM, BigDecimal, I_M_HU_PI_Item_Product, boolean)}
	 * by creating an <b>aggregate</b> HU with a qty of 80 (representing two IFCOs) and then splitting one.
	 * 
	 * @param isOwnPackingMaterials
	 */
	@Theory
	public void testSplitAggregateCU_To_NewTUs_1Tomato(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		final I_M_HU cuToSplit = mkAggregateCUToSplit("80"); // match the IFCOs capacity

		// invoke the method under test
		final List<I_M_HU> newTUs = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewTUs(cuToSplit, data.helper.pTomato, data.helper.uomKg, BigDecimal.ONE, data.piTU_Item_Product_Bag_8KgTomatoes, isOwnPackingMaterials);

		assertThat(newTUs.size(), is(1));

		// data.helper.commitAndDumpHU(cuToSplit);

		final Node cuToSplitXML = HUXmlConverter.toXml(cuToSplit);
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI[@HUStatus='P'])", is("1")));
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @Qty='40.000' and @C_UOM_Name='Kg'])", is("1")));

		final I_M_HU parentOfCUToSplit = cuToSplit.getM_HU_Item_Parent().getM_HU();
		// data.helper.commitAndDumpHU(parentOfCUToSplit);
		// the source TU now needs to contain one haggregate HU that represent the remaining "untouched" IFCO with a quantity of 40 and a new "real" IFCO with a qunatity of 39.
		final Node parentOfCUToSplitXML = HUXmlConverter.toXml(parentOfCUToSplit);
		assertThat(parentOfCUToSplitXML, hasXPath("count(HU-LU_Palet[@HUStatus='P'])", is("1")));
		assertThat(parentOfCUToSplitXML, hasXPath("count(HU-LU_Palet/Storage[@M_Product_Value='Tomato' and @Qty='79.000' and @C_UOM_Name='Kg'])", is("1")));
		assertThat(parentOfCUToSplitXML, hasXPath("count(HU-LU_Palet/Item[@ItemType='HU']/HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @Qty='39.000' and @C_UOM_Name='Kg'])", is("1")));
		assertThat(parentOfCUToSplitXML, hasXPath("count(HU-LU_Palet/Item[@ItemType='HA']/Storage[@M_Product_Value='Tomato' and @Qty='40.000' and @C_UOM_Name='Kg'])", is("1")));

		final Node newTUXML = HUXmlConverter.toXml(newTUs.get(0));

		assertThat(newTUXML, hasXPath("count(HU-TU_Bag[@HUStatus='P'])", is("1")));
		assertThat(newTUXML, hasXPath("string(HU-TU_Bag/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));
		assertThat(newTUXML, hasXPath("count(HU-TU_Bag/Storage[@M_Product_Value='Tomato' and @Qty='1.000' and @C_UOM_Name='Kg'])", is("1")));
	}

	@Theory
	public void testSplitRealCU_To_NewTUs_1Tomato_TU_Capacity_40(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		final I_M_HU cuToSplit = mkRealStandAloneCUToSplit("2");

		// invoke the method under test
		final List<I_M_HU> newTUs = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewTUs(cuToSplit, data.helper.pTomato, data.helper.uomKg, new BigDecimal("2"), data.piTU_Item_Product_IFCO_40KgTomatoes, isOwnPackingMaterials);

		assertThat(newTUs.size(), is(1));

		// data.helper.commitAndDumpHU(newTUs.get(0));

		final Node cuToSplitXML = HUXmlConverter.toXml(cuToSplit);
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI[@HUStatus='D'])", is("1")));
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @Qty='0.000' and @C_UOM_Name='Kg'])", is("1")));

		final Node newTUXML = HUXmlConverter.toXml(newTUs.get(0));

		assertThat(newTUXML, hasXPath("count(HU-TU_IFCO[@HUStatus='P'])", is("1")));
		assertThat(newTUXML, hasXPath("string(HU-TU_IFCO/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));
		assertThat(newTUXML, hasXPath("count(HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @Qty='2.000' and @C_UOM_Name='Kg'])", is("1")));
	}

	/**
	 * Run {@link HUTransferService#splitCU_To_NewTUs(I_M_HU, org.compiere.model.I_M_Product, org.compiere.model.I_C_UOM, BigDecimal, I_M_HU_PI_Item_Product, boolean)}
	 * by splitting a CU-quantity of 40 onto new TUs with a CU-capacity of 8 each.
	 * 
	 * @param isOwnPackingMaterials
	 */
	@Theory
	public void testSplitRealCU_To_NewTUs_40Tomatoes_TU_Capacity_8(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		// TODO talk about this behavior with mark
		final I_M_HU cuToSplit = mkRealStandAloneCUToSplit("40");

		// invoke the method under test
		final List<I_M_HU> newTUs = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewTUs(cuToSplit, data.helper.pTomato, data.helper.uomKg, new BigDecimal("40"), data.piTU_Item_Product_Bag_8KgTomatoes, isOwnPackingMaterials);

		assertThat(newTUs.size(), is(5));

		// data.helper.commitAndDumpHU(newTUs.get(0));

		final Node cuToSplitXML = HUXmlConverter.toXml(cuToSplit);
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI[@HUStatus='D'])", is("1")));
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @Qty='0.000' and @C_UOM_Name='Kg'])", is("1")));

		for (final I_M_HU newTU : newTUs)
		{
			final Node newTUXML = HUXmlConverter.toXml(newTU);

			assertThat(newTUXML, hasXPath("count(HU-TU_Bag[@HUStatus='P'])", is("1")));
			assertThat(newTUXML, hasXPath("string(HU-TU_Bag/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));
			assertThat(newTUXML, hasXPath("count(HU-TU_Bag/Storage[@M_Product_Value='Tomato' and @Qty='8.000' and @C_UOM_Name='Kg'])", is("1")));
		}
	}

	@Theory
	public void testSplitRealCU_To_ExistingRealTU()
	{
		// prepare the existing TU
		// just use the testee as a tool here, to create our "real" TU.
		final I_M_HU cuHU = mkRealStandAloneCUToSplit("20");
		final List<I_M_HU> existingTUs = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewTUs(cuHU, data.helper.pTomato, data.helper.uomKg, new BigDecimal("20"), data.piTU_Item_Product_IFCO_40KgTomatoes, false);
		assertThat(existingTUs.size(), is(1));
		final I_M_HU existingTU = existingTUs.get(0);
		assertThat(handlingUnitsBL.isAggregateHU(existingTU), is(false));

		final Node existingTUBeforeXML = HUXmlConverter.toXml(existingTU);
		assertThat(existingTUBeforeXML, not(hasXPath("HU-TU_IFCO/M_HU_Item_Parent_ID"))); // verify that there is still no parent HU
		assertThat(existingTUBeforeXML, hasXPath("count(HU-TU_IFCO[@HUStatus='P'])", is("1")));
		assertThat(existingTUBeforeXML, hasXPath("count(HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @Qty='20.000' and @C_UOM_Name='Kg'])", is("1")));

		// prepare the CU to split
		final I_M_HU cuToSplit = mkRealStandAloneCUToSplit("20");

		// invoke the method under test
		HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_ExistingTU(cuToSplit, data.helper.pTomato, data.helper.uomKg, new BigDecimal("20"), existingTU);

		// the cu we split from is destroyed
		final Node cuToSplitXML = HUXmlConverter.toXml(cuToSplit);
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI[@HUStatus='D'])", is("1")));
		assertThat(cuToSplitXML, hasXPath("count(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @Qty='0.000' and @C_UOM_Name='Kg'])", is("1")));

		final Node existingTUXML = HUXmlConverter.toXml(existingTU);
		assertThat(existingTUXML, not(hasXPath("HU-TU_IFCO/M_HU_Item_Parent_ID"))); // verify that there is still no parent HU
		assertThat(existingTUXML, hasXPath("count(HU-TU_IFCO[@HUStatus='P'])", is("1")));
		assertThat(existingTUXML, hasXPath("count(HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @Qty='40.000' and @C_UOM_Name='Kg'])", is("1")));
	}

	@Test
	public void testSplitRealCU_To_ExistingAggregateTU()
	{
		final I_M_HU existingTU = mkAggregateCUToSplit("80");

		final Node existingTUBeforeXML = HUXmlConverter.toXml(existingTU);
		assertThat(existingTUBeforeXML, hasXPath("string(HU-VirtualPI/@HUStatus)", is("P")));
		assertThat(existingTUBeforeXML, hasXPath("string(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("80.000")));

		final I_M_HU cuToSplit = mkRealStandAloneCUToSplit("20");

		// invoke the method under test
		HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_ExistingTU(cuToSplit, data.helper.pTomato, data.helper.uomKg, new BigDecimal("20"), existingTU);

		// the cu we split from is destroyed
		final Node cuToSplitXML = HUXmlConverter.toXml(cuToSplit);
		assertThat(cuToSplitXML, hasXPath("string(HU-VirtualPI/@HUStatus)", is("D")));
		assertThat(cuToSplitXML, hasXPath("string(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("0.000")));

		// the existing TU to which we wanted to add stuff is unchanged, but it now has a "real-TU" sibling
		final Node existingTUXML = HUXmlConverter.toXml(existingTU);
		assertThat(existingTUXML, hasXPath("string(HU-VirtualPI/@HUStatus)", is("P")));
		assertThat(existingTUXML, hasXPath("string(HU-VirtualPI/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("80.000")));

		final I_M_HU fullTargetHU = existingTU.getM_HU_Item_Parent().getM_HU();
		final Node fullTargetHUXML = HUXmlConverter.toXml(fullTargetHU);
		// data.helper.commitAndDumpHU(fullTargetHU);
		assertThat(fullTargetHUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/HU-VirtualPI/@M_HU_ID)", is(Integer.toString(existingTU.getM_HU_ID())))); // fullTargetHU contains existingTU
		assertThat(fullTargetHUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HU']/HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("20.000"))); // fullTargetHU also contains a real IFCO with 20

	}

	/**
	 * Verifies that if {@link HUTransferService#splitTU_To_NewTUs(I_M_HU, BigDecimal, boolean)} is run with the source TU's full qty or more and since .
	 * 
	 * @param isOwnPackingMaterials
	 */
		@Test
	public void testSplitAggregateTU_To_NewTUs_MaxValueParent()
	{
		final I_M_HU tuToSplit = mkAggregateCUToSplit("80");
		assertThat(handlingUnitsDAO.retrieveParentItem(tuToSplit), notNullValue()); // guard: tuToSplit shall have a parent

		// invoke the method under test
		final List<I_M_HU> newTUs = HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_NewTUs(tuToSplit,
						new BigDecimal("4"), // tuQty=4; we only have 2 TUs in the source
						false); // true/false, doesn't matter
		assertThat(newTUs.size(), is(2));

		assertThat(handlingUnitsDAO.retrieveParentItem(newTUs.get(0)), nullValue());
		assertThat(handlingUnitsDAO.retrieveParentItem(newTUs.get(1)), nullValue());
	}

	@Theory
	public void testSplitAggregateTU_To_NewTUs(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		final I_M_HU tuToSplit = mkAggregateCUToSplit("80");

		// invoke the method under test
		final List<I_M_HU> newTUs = HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_NewTUs(tuToSplit,
						new BigDecimal("1"), // tuQty=1; we have 2 TUs in the source, so we will will only expect 1x40 to be actually loaded
						isOwnPackingMaterials);
		assertThat(newTUs.size(), is(1));

		final Node newTUXML = HUXmlConverter.toXml(newTUs.get(0));
		assertThat(newTUXML, not(hasXPath("HU-TU_IFCO/M_HU_Item_Parent_ID"))); // verify that there is no parent HU
		assertThat(newTUXML, hasXPath("string(HU-TU_IFCO/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));
		assertThat(newTUXML, hasXPath("string(HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("40.000")));
	}

	/**
	 * Verifies the nothing is changed if {@link HUTransferService#splitTU_To_NewTUs(I_M_HU, BigDecimal, boolean)} is run with the source TU's full qty or more.
	 * 
	 * @param isOwnPackingMaterials
	 */
	@Test
	public void testSplitRealTU_To_NewTUs_MaxValue()
	{
		// prepare the existing TU
		// just use the testee as a tool here, to create our "real" TU.
		final I_M_HU cuHU = mkRealStandAloneCUToSplit("20");
		final List<I_M_HU> tusToSplit = HUTransferService.get(data.helper.getHUContext())
				.splitCU_To_NewTUs(cuHU, data.helper.pTomato, data.helper.uomKg, new BigDecimal("20"), data.piTU_Item_Product_IFCO_40KgTomatoes, false);
		assertThat(tusToSplit.size(), is(1));
		final I_M_HU tuToSplit = tusToSplit.get(0);
		assertThat(handlingUnitsBL.isAggregateHU(tuToSplit), is(false)); // guard; make sure it's "real"

		// invoke the method under test
		final List<I_M_HU> newTUs = HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_NewTUs(tuToSplit,
						new BigDecimal("4"), // tuQty=4; we only have 1 TU in the source which only holds 20kg
						false); // true/false, doesn't matter
		assertThat(newTUs.size(), is(0)); // we transfer 20kg, one bag holds 8kg, so we expect 2 full bags and one partially filled bag
	}

	/**
	 * Similar to {@link #testSplitAggregateTU_To_NewTUs_MaxValue()}, but here the source TU is on a pallet.<br>
	 * So this time, it shall be taken off the pallet.
	 * 
	 * @param isOwnPackingMaterials
	 */
	@Theory
	public void testSplitRealTU_To_NewTUs(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		// prepare the existing TU
		// just use the testee as a tool here, to create our "real" TU.
		final I_M_HU tuToSplit;
		final I_M_HU lu; // the parent LU of the TU to split;
		{
			final I_M_HU cuHU = mkRealStandAloneCUToSplit("20");
			final List<I_M_HU> tusToSplit = HUTransferService.get(data.helper.getHUContext())
					.splitCU_To_NewTUs(cuHU, data.helper.pTomato, data.helper.uomKg, new BigDecimal("20"), data.piTU_Item_Product_IFCO_40KgTomatoes, false);
			assertThat(tusToSplit.size(), is(1));
			tuToSplit = tusToSplit.get(0);
			assertThat(handlingUnitsBL.isAggregateHU(tuToSplit), is(false)); // guard; make sure it's "real"

			final List<I_M_HU> lus = HUTransferService.get(data.helper.getHUContext())
					.splitTU_To_NewLUs(tuToSplit, BigDecimal.ONE, data.piLU_Item_IFCO, isOwnPackingMaterials);
			// get the LU and verify that it's properly linked with toToSplit
			{
				assertThat(lus.size(), is(1));
				lu = lus.get(0);
				final List<I_M_HU> includedHUs = handlingUnitsDAO.retrieveIncludedHUs(lu);
				assertThat(includedHUs.size(), is(1));
				assertThat(includedHUs.get(0).getM_HU_ID(), is(tuToSplit.getM_HU_ID()));

				assertThat(tuToSplit.getM_HU_Item_Parent().getM_HU_ID(), is(lu.getM_HU_ID()));
			}
		}
		// invoke the method under test
		final List<I_M_HU> newTUs = HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_NewTUs(tuToSplit,
						new BigDecimal("1"), // tuQty=1;
						isOwnPackingMaterials);
		assertThat(newTUs.size(), is(1)); // we transfer 20kg, one IFCO holds 40kg, so we expect 1 IFCO
		assertThat(newTUs.get(0).getM_HU_ID(), is(tuToSplit.getM_HU_ID()));
		assertThat(newTUs.get(0).getM_HU_Item_Parent(), nullValue());

		assertThat(handlingUnitsDAO.retrieveIncludedHUs(lu).isEmpty(), is(true));
	}

	@Theory
	public void testSplitAggregateTU_To_OneNewLU(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		final I_M_HU tuToSplit = mkAggregateCUToSplit("80");
		assertThat(handlingUnitsBL.isAggregateHU(tuToSplit), is(true)); // guard; make sure it's aggregate

		// invoke the method under test
		final List<I_M_HU> newLUs = HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_NewLUs(tuToSplit,
						new BigDecimal("4"), // tuQty=4; we only have 2 TUs in the source which hold 40kg each, so we will will expect 2x40 to be actually loaded
						data.piLU_Item_IFCO,
						isOwnPackingMaterials);
		assertThat(newLUs.size(), is(1)); // we transfered 80kg, the target TUs are still IFCOs one IFCO still holds 40kg, one LU holds 5 IFCOS, so we expect one LU to suffice
		// data.helper.commitAndDumpHU(newLUs.get(0));

		final Node luXML = HUXmlConverter.toXml(newLUs.get(0));
		assertThat(luXML, not(hasXPath("HU-LU_Palet/M_HU_Item_Parent_ID"))); // verify that the LU has no parent HU
		assertThat(luXML, hasXPath("string(HU-LU_Palet/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));
		assertThat(luXML, hasXPath("string(HU-LU_Palet/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("80.000")));

		// the pallet's included aggregate HU is 'tuToSplit'
		assertThat(luXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/HU-VirtualPI/@M_HU_ID)", is(Integer.toString(tuToSplit.getM_HU_ID()))));
		assertThat(luXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/@M_HU_PI_Item_ID)", is(Integer.toString(data.piLU_Item_IFCO.getM_HU_PI_Item_ID()))));

		assertThat(luXML, hasXPath("count(HU-LU_Palet/Item[@ItemType='HA' and @Qty='2'])", is("1")));
		assertThat(luXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA' and @Qty='2']/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("80.000")));
	}

	@Theory
	public void testSplitAggregateTU_To_MultipleNewLUs(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		final I_M_HU tuToSplit = mkAggregateCUToSplit("240"); // 6 TUs
		assertThat(handlingUnitsBL.isAggregateHU(tuToSplit), is(true)); // guard; make sure it's aggregate

		// invoke the method under test
		final List<I_M_HU> newLUs = HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_NewLUs(tuToSplit,
						new BigDecimal("6"), // tuQty=6;
						data.piLU_Item_IFCO,
						isOwnPackingMaterials);
		// data.helper.commitAndDumpHUs(newLUs);
		assertThat(newLUs.size(), is(2)); // we have 6 TUs in the source; one pallet can old 5 IFCOS, to we expect two pallets.
		{
			final Node lu1XML = HUXmlConverter.toXml(newLUs.get(0));
			assertThat(lu1XML, not(hasXPath("HU-LU_Palet/M_HU_Item_Parent_ID"))); // verify that the LU has no parent HU
			assertThat(lu1XML, hasXPath("string(HU-LU_Palet/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));

			assertThat(lu1XML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/@M_HU_PI_Item_ID)", is(Integer.toString(data.piLU_Item_IFCO.getM_HU_PI_Item_ID()))));

			assertThat(lu1XML, hasXPath("string(HU-LU_Palet/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("200.000")));
			assertThat(lu1XML, hasXPath("count(HU-LU_Palet/Item[@ItemType='HA' and @Qty='5'])", is("1")));
			assertThat(lu1XML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA' and @Qty='5']/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("200.000")));
		}
		{
			final Node lu2XML = HUXmlConverter.toXml(newLUs.get(1));
			assertThat(lu2XML, not(hasXPath("HU-LU_Palet/M_HU_Item_Parent_ID"))); // verify that the LU has no parent HU
			assertThat(lu2XML, hasXPath("string(HU-LU_Palet/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));

			assertThat(lu2XML, hasXPath("string(HU-LU_Palet/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("40.000")));
		}
	}

	@Theory
	public void testSplitRealTU_To_NewLU(
			@FromDataPoints("isOwnPackingMaterials") final boolean isOwnPackingMaterials)
	{
		// prepare the existing TU
		final I_M_HU cuHU = mkRealCUWithTUToSplit("20");
		final I_M_HU tuToSplit = cuHU.getM_HU_Item_Parent().getM_HU();
		assertThat(handlingUnitsBL.isAggregateHU(tuToSplit), is(false)); // guard; make sure it's "real"

		// invoke the method under test
		final List<I_M_HU> newLUs = HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_NewLUs(tuToSplit,
						new BigDecimal("4"), // tuQty=4; we only have 1 TU in the source which only holds 20kg, so we will expect the TU to be moved
						data.piLU_Item_IFCO,
						isOwnPackingMaterials);

		assertThat(newLUs.size(), is(1)); // we transfered 20kg, the target TUs are still IFCOs one IFCO still holds 40kg, one LU holds 5 IFCOS, so we expect one LU with one IFCO to suffice
		// data.helper.commitAndDumpHU(newLUs.get(0));
		// the LU shall contain 'tuToSplit'
		final Node newLUXML = HUXmlConverter.toXml(newLUs.get(0));
		assertThat(newLUXML, not(hasXPath("HU-LU_Palet/M_HU_Item_Parent_ID"))); // verify that the LU has no parent HU
		assertThat(newLUXML, hasXPath("string(HU-LU_Palet/@HUPlanningReceiptOwnerPM)", is(Boolean.toString(isOwnPackingMaterials))));

		assertThat(newLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HU']/@M_HU_PI_Item_ID)", is(Integer.toString(data.piLU_Item_IFCO.getM_HU_PI_Item_ID()))));
		assertThat(newLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HU']/HU-TU_IFCO/@M_HU_ID)", is(Integer.toString(tuToSplit.getM_HU_ID()))));

		assertThat(newLUXML, hasXPath("string(HU-LU_Palet/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("20.000")));
		assertThat(newLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HU']/HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("20.000")));
	}

	/**
	 * Split an aggregate TU to a LU that contains a "real" TU
	 */
	@Test
	public void testSplitAggregateTU_to_existingLU_withRealTU()
	{
		// use the testee as a tool to get our existing LU
		final I_M_HU existingLU;
		{
			final I_M_HU cuHU = mkRealStandAloneCUToSplit("20");
			final List<I_M_HU> existingTUs = HUTransferService.get(data.helper.getHUContext())
					.splitCU_To_NewTUs(cuHU, data.helper.pTomato, data.helper.uomKg, new BigDecimal("20"), data.piTU_Item_Product_IFCO_40KgTomatoes, false);
			assertThat(existingTUs.size(), is(1));
			final I_M_HU exitingTu = existingTUs.get(0);
			assertThat(handlingUnitsBL.isAggregateHU(exitingTu), is(false)); // guard; make sure it's "real"

			final List<I_M_HU> existingLUs = HUTransferService.get(data.helper.getHUContext())
					.splitTU_To_NewLUs(exitingTu,
							new BigDecimal("4"), // tuQty=4; we only have 1 TU in the source which only holds 20kg, so we will will expect 1x20 to be actually loaded
							data.piLU_Item_IFCO,
							false);
			assertThat(existingLUs.size(), is(1));
			// data.helper.commitAndDumpHU(existingLUs.get(0));
			existingLU = existingLUs.get(0); //

			// guard: the contained TU is "real"
			final Node existingLUXML = HUXmlConverter.toXml(existingLU);
			assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HU']/HU-TU_IFCO/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("20.000")));
		}

		// now create the aggregation TU we are going to split
		final I_M_HU tuToSplit = mkAggregateCUToSplit("80");

		// invoke the method under test
		HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_ExistingLU(tuToSplit,
						new BigDecimal("4"), // tuQty=4; we only have 2 TU in the source which hold 40kg each, so we will will expect 2x40 to be actually loaded
						existingLU);

		// we had 20 and loaded 80, so we now expect 100
		final Node existingLUXML = HUXmlConverter.toXml(existingLU);
		assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("100.000")));
		// data.helper.commitAndDumpHU(existingLU);
	}

	/**
	 * Split an aggregate TU to a LU that already contains an aggregated TU
	 */
	@Test
	public void testSplitAggregateTU_To_existingLU_withAggregateTU()
	{
		// use the testee as a tool to get our existing LU
		final I_M_HU existingLU;
		{
			final I_M_HU exitingTu = mkAggregateCUToSplit("80");
			assertThat(handlingUnitsBL.isAggregateHU(exitingTu), is(true)); // guard; make sure it's "aggregate"

			final List<I_M_HU> existingLUs = HUTransferService.get(data.helper.getHUContext())
					.splitTU_To_NewLUs(exitingTu,
							new BigDecimal("4"), // tuQty=4; we only have 2 TUs in the source which only holds 80kg, so we will will expect 2x40 to be actually loaded onto one LU
							data.piLU_Item_IFCO,
							false);
			assertThat(existingLUs.size(), is(1));
			// data.helper.commitAndDumpHU(existingLUs.get(0));
			existingLU = existingLUs.get(0); //

			// guard: the contained TU is "real"
			final Node existingLUXML = HUXmlConverter.toXml(existingLU);
			assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("80.000"))); // the LU has 80kg
			assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("80.000"))); // those 80kg are contained in one aggreagate HU

			// that aggregate HU represents two IFCOS
			assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/@Qty)", is("2")));
			assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/@M_HU_PI_Item_ID)", is(Integer.toString(data.piLU_Item_IFCO.getM_HU_PI_Item_ID()))));
		}

		// now create the aggregation TU we are going to split
		final I_M_HU tuToSplit = mkAggregateCUToSplit("80");

		// invoke the method under test
		HUTransferService.get(data.helper.getHUContext())
				.splitTU_To_ExistingLU(tuToSplit,
						new BigDecimal("4"), // tuQty=4; we only have 2 TU in the source which hold 40kg each, so we will will expect 2x40 to be actually loaded
						existingLU);

		// we had 80 and loaded 80, so we now expect 160
		final Node existingLUXML = HUXmlConverter.toXml(existingLU);
		assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Storage[@M_Product_Value='Tomato' and @C_UOM_Name='Kg']/@Qty)", is("160.000")));
		assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/@Qty)", is("4")));
		assertThat(existingLUXML, hasXPath("string(HU-LU_Palet/Item[@ItemType='HA']/@M_HU_PI_Item_ID)", is(Integer.toString(data.piLU_Item_IFCO.getM_HU_PI_Item_ID()))));
		// data.helper.commitAndDumpHU(existingLU);
	}

	// TODO: test with TUs that have multiple different CUs in them

	private I_M_HU mkRealStandAloneCUToSplit(final String strCuQty)
	{
		final HUProducerDestination producer = HUProducerDestination.ofVirtualPI();
		data.helper.load(producer, data.helper.pTomato, new BigDecimal(strCuQty), data.helper.uomKg);

		final List<I_M_HU> createdCUs = producer.getCreatedHUs();
		assertThat(createdCUs.size(), is(1));

		final I_M_HU cuToSplit = createdCUs.get(0);
		return cuToSplit;
	}

	private I_M_HU mkRealCUWithTUToSplit(final String strCuQty)
	{
		final LUTUProducerDestination lutuProducer = new LUTUProducerDestination();
		lutuProducer.setNoLU();
		lutuProducer.setTUPI(data.piTU_IFCO);

		data.helper.load(lutuProducer, data.helper.pTomato, new BigDecimal(strCuQty), data.helper.uomKg);
		final List<I_M_HU> createdTUs = lutuProducer.getCreatedHUs();

		assertThat(createdTUs.size(), is(1));

		final List<I_M_HU> createdCUs = handlingUnitsDAO.retrieveIncludedHUs(createdTUs.get(0));
		assertThat(createdCUs.size(), is(1));

		final I_M_HU cuToSplit = createdCUs.get(0);

		return cuToSplit;
	}

	private I_M_HU mkAggregateCUToSplit(final String strCuQty)
	{

		final LUTUProducerDestination lutuProducer = new LUTUProducerDestination();
		lutuProducer.setLUItemPI(data.piLU_Item_IFCO);
		lutuProducer.setLUPI(data.piLU);
		lutuProducer.setTUPI(data.piTU_IFCO);
		lutuProducer.setMaxTUsPerLU(Integer.MAX_VALUE); // allow as many TUs on that one pallet as we want
		data.helper.load(lutuProducer, data.helper.pTomato, new BigDecimal(strCuQty), data.helper.uomKg);
		final List<I_M_HU> createdLUs = lutuProducer.getCreatedHUs();

		assertThat(createdLUs.size(), is(1));
		// data.helper.commitAndDumpHU(createdLUs.get(0));

		final List<I_M_HU> createdAggregateHUs = handlingUnitsDAO.retrieveIncludedHUs(createdLUs.get(0));
		assertThat(createdAggregateHUs.size(), is(1));

		final I_M_HU cuToSplit = createdAggregateHUs.get(0);
		assertThat(handlingUnitsBL.isAggregateHU(cuToSplit), is(true));
		assertThat(cuToSplit.getM_HU_Item_Parent().getM_HU_PI_Item_ID(), is(data.piLU_Item_IFCO.getM_HU_PI_Item_ID()));

		return cuToSplit;
	}
}
