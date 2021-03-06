/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opends.server.TestCaseUtils;
import org.opends.server.util.ServerConstants;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.types.CommonSchemaElements.*;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.types.CommonSchemaElements} class and
 * derived classes.
 */
public abstract class TestCommonSchemaElements extends TypesTestCase {
  /**
   * Internal class to simplify construction of attribute types.
   *
   * @param <T>
   *          The type of definition that this builder constructs.
   */
  protected static abstract class SchemaDefinitionBuilder<T extends CommonSchemaElements> {
    /** The primary name to use for this attribute type. */
    private String primaryName;

    /** The set of names for this attribute type. */
    private List<String> names;

    /** The OID that may be used to reference this attribute type. */
    private String oid;

    /** The description for this attribute type. */
    private String description;

    /** Indicates whether this attribute type is declared "obsolete". */
    private boolean isObsolete;

    /**
     * The set of additional name-value pairs associated with this
     * attribute type definition.
     */
    private Map<String, List<String>> extraProperties;



    /** Reset the builder to its initial state. */
    private void reset() {
      this.primaryName = null;
      this.names = null;
      this.oid = null;
      this.description = null;
      this.isObsolete = false;
      this.extraProperties = null;

      resetBuilder();
    }



    /**
     * Create a new attribute type builder.
     */
    protected SchemaDefinitionBuilder() {
      reset();
    }



    /**
     * Create a new attribute type builder.
     *
     * @param primaryName
     *          The attribute type primary name.
     * @param oid
     *          The attribute type OID.
     */
    protected SchemaDefinitionBuilder(String primaryName, String oid) {
      reset();

      this.primaryName = primaryName;
      this.oid = oid;
    }



    /**
     * Construct an attribute type based on the properties of the
     * builder.
     *
     * @return The new attribute type.
     */
    public final T getInstance() {
      if (oid == null) {
        throw new IllegalStateException("Null OID.");
      }

      T instance = buildInstance(primaryName, names, oid,
          description, isObsolete, extraProperties);

      // Reset the internal state.
      reset();

      return instance;
    }



    /**
     * Build a new instance using this builder.
     *
     * @param primaryName
     *          The primary name.
     * @param names
     *          The optional names.
     * @param oid
     *          The OID.
     * @param description
     *          The optional description.
     * @param isObsolete
     *          Whether or not the definition is obsolete.
     * @param extraProperties
     *          The extra properties.
     * @return Returns the newly constructed definition.
     */
    protected abstract T buildInstance(String primaryName,
        Collection<String> names, String oid, String description,
        boolean isObsolete, Map<String, List<String>> extraProperties);



    /**
     * Reset the internal state of the builder.
     */
    protected abstract void resetBuilder();



    /**
     * Set the description.
     *
     * @param description
     *          The description.
     */
    public final void setDescription(String description) {
      this.description = description;
    }



    /**
     * Add extra property value(s).
     *
     * @param name
     *          The name of the extra property.
     * @param values
     *          The value(s) of the extra property.
     */
    public final void addExtraProperty(String name, String... values) {
      if (name == null) {
        throw new NullPointerException("Null extra property name");
      }

      if (values == null) {
        throw new NullPointerException("Null extra property values");
      }

      if (extraProperties == null) {
        extraProperties = new HashMap<>();
      }

      List<String> l = extraProperties.get(name);
      if (l == null) {
        l = new ArrayList<>();
        extraProperties.put(name, l);
      }
      l.addAll(Arrays.asList(values));
    }



    /**
     * Set the isObsolete.
     *
     * @param isObsolete
     *          The isObsolete.
     */
    public final void setObsolete(boolean isObsolete) {
      this.isObsolete = isObsolete;
    }



    /**
     * Set the oid.
     *
     * @param oid
     *          The oid.
     */
    public final void setOid(String oid) {
      if (oid == null) {
        throw new NullPointerException("Null OID");
      }

      this.oid = oid;
    }



    /**
     * Set the primaryName.
     *
     * @param primaryName
     *          The primaryName.
     */
    public final void setPrimaryName(String primaryName) {
      this.primaryName = primaryName;
    }



    /**
     * Add attribute type name(s).
     *
     * @param names
     *          The attribute type name(s) to add.
     */
    public final void addTypeNames(String... names) {
      if (names == null) {
        throw new NullPointerException("Null names");
      }

      if (this.names == null) {
        this.names = new LinkedList<>();
      }

      this.names.addAll(Arrays.asList(names));
    }
  }



  /**
   * Create a new schema definition builder.
   *
   * @param name
   *          The schema definition's primary name.
   * @param oid
   *          The OID of the schema definition.
   * @return The new builder.
   */
  protected abstract SchemaDefinitionBuilder getBuilder(String name,
      String oid);



  /**
   * Once-only initialization.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @BeforeClass
  public final void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll
    // start the server.
    TestCaseUtils.startServer();
  }



  /**
   * Check that the primary name is added to the set of names.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testConstructorPrimaryName() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    CommonSchemaElements d = builder.getInstance();

    Assert.assertTrue(d.hasName("testtype"));
    Assert.assertFalse(d.hasName("xxx"));
  }



  /**
   * Check that the type names are accessible.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testConstructorTypeNames() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");

    builder.addTypeNames("testNameAlias", "anotherNameAlias");
    CommonSchemaElements d = builder.getInstance();

    Assert.assertTrue(d.hasName("testtype"));
    Assert.assertTrue(d.hasName("testnamealias"));
    Assert.assertTrue(d.hasName("anothernamealias"));
  }



  /**
   * Create test data for testing the
   * {@link CommonSchemaElements#equals(Object)} method.
   *
   * @return Returns the array of test data.
   */
  @DataProvider(name = "equalsTestData")
  public final Object[][] createEqualsTestData() {
    return new Object[][] {
        { "testType", "1.2.3", "testType", "1.2.3", true },
        { "testType", "1.2.3", "xxx", "1.2.3", false },
        { "testType", "1.2.3", "testType", "1.2.4", true },
        { "testType", "1.2.3", "xxx", "1.2.4", false } };
  }



  /**
   * Check that the equals operator works as expected.
   *
   * @param name1
   *          The first primary name.
   * @param oid1
   *          The first oid.
   * @param name2
   *          The second primary name.
   * @param oid2
   *          The second oid.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "equalsTestData")
  public final void testEquals(String name1, String oid1,
      String name2, String oid2, boolean result) throws Exception {
    SchemaDefinitionBuilder builder1 = getBuilder(name1, oid1);
    CommonSchemaElements d1 = builder1.getInstance();

    SchemaDefinitionBuilder builder2 = getBuilder(name2, oid2);
    CommonSchemaElements d2 = builder2.getInstance();

    Assert.assertEquals(d1.equals(d2), result);
    Assert.assertEquals(d2.equals(d1), result);
  }



  /**
   * Check that the hasCode method operator works as expected.
   *
   * @param name1
   *          The first primary name.
   * @param oid1
   *          The first oid.
   * @param name2
   *          The second primary name.
   * @param oid2
   *          The second oid.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "equalsTestData")
  public final void testHashCode(String name1, String oid1,
      String name2, String oid2, boolean result) throws Exception {
    SchemaDefinitionBuilder builder1 = getBuilder(name1, oid1);
    CommonSchemaElements d1 = builder1.getInstance();

    SchemaDefinitionBuilder builder2 = getBuilder(name2, oid2);
    CommonSchemaElements d2 = builder2.getInstance();

    Assert.assertEquals(d1.hashCode() == d2.hashCode(), result);
  }



  /**
   * Check that the {@link CommonSchemaElements#getDescription()}
   * method returns <code>null</code> when there is no description.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetDescriptionDefault() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("test", "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertNull(d.getDescription());
  }



  /**
   * Check that the {@link CommonSchemaElements#getDescription()}
   * method returns a description.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetDescription() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("test", "1.2.3");
    builder.setDescription("hello");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertEquals(d.getDescription(), "hello");
  }



  /**
   * Check that the {@link CommonSchemaElements#getExtraProperties()} method
   * returns <code>null</code> when there is no property.
   */
  @Test
  public final void testGetExtraPropertyDefault() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("test", "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertNull(d.getExtraProperties().get("test"));
  }



  /**
   * Check that the {@link CommonSchemaElements#getExtraProperties()} method
   * returns values.
   */
  @Test
  public final void testGetExtraProperties() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("test", "1.2.3");
    String[] expectedValues = new String[] { "one", "two" };
    builder.addExtraProperty("test", expectedValues);
    CommonSchemaElements d = builder.getInstance();

    List<String> values = d.getExtraProperties().get("test");
    Assert.assertNotNull(values);
    for (int i = 0; i < values.size(); i++)
    {
      Assert.assertEquals(values.get(i), expectedValues[i]);
    }
  }



  /**
   * Check that the
   * {@link CommonSchemaElements#getExtraProperties()} method.
   */
  @Test
  public final void testGetExtraPropertyNames() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("test", "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertFalse(d.getExtraProperties().keySet().iterator().hasNext());
  }



  /**
   * Check that the {@link CommonSchemaElements#getNameOrOID()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNameOrOIDReturnsOID() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder(null, "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertEquals(d.getNameOrOID(), "1.2.3");
  }



  /**
   * Check that the {@link CommonSchemaElements#getNameOrOID()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNameOrOIDReturnsPrimaryName()
      throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertEquals(d.getNameOrOID(), "testType");
  }



  /**
   * Check that the {@link CommonSchemaElements#getNameOrOID()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNameOrOIDReturnsOtherName()
      throws Exception {
    SchemaDefinitionBuilder builder = getBuilder(null, "1.2.3");
    builder.addTypeNames("anotherName");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertEquals(d.getNameOrOID(), "anotherName");
  }



  /**
   * Check that the {@link CommonSchemaElements#getNormalizedNames()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNormalizedNames() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    builder.addTypeNames("anotherName", "yetAnotherName");
    CommonSchemaElements d = builder.getInstance();

    boolean gotTestType = false;
    boolean gotAnotherName = false;
    boolean gotYetAnotherName = false;

    for (String name : d.getNormalizedNames()) {
      if (name.equals("testtype")) {
        gotTestType = true;
      } else if (name.equals("anothername")) {
        gotAnotherName = true;
      } else if (name.equals("yetanothername")) {
        gotYetAnotherName = true;
      } else {
        Assert.fail("Got unexpected normalized name: " + name);
      }
    }

    Assert.assertTrue(gotTestType && gotAnotherName
        && gotYetAnotherName);
  }



  /**
   * Check that the {@link CommonSchemaElements#getUserDefinedNames()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetUserDefinedNames() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    builder.addTypeNames("anotherName", "yetAnotherName");
    CommonSchemaElements d = builder.getInstance();

    boolean gotTestType = false;
    boolean gotAnotherName = false;
    boolean gotYetAnotherName = false;

    for (String name : d.getUserDefinedNames()) {
      if (name.equals("testType")) {
        gotTestType = true;
      } else if (name.equals("anotherName")) {
        gotAnotherName = true;
      } else if (name.equals("yetAnotherName")) {
        gotYetAnotherName = true;
      } else {
        Assert.fail("Got unexpected user defined name: " + name);
      }
    }

    Assert.assertTrue(gotTestType && gotAnotherName
        && gotYetAnotherName);
  }



  /**
   * Check that the
   * {@link CommonSchemaElements#getNormalizedPrimaryNameOrOID()} method
   * returns <code>null</code> when there is no primary name.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNormalizedPrimaryNameOrOIDDefault()
      throws Exception {
    SchemaDefinitionBuilder builder = getBuilder(null, "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertEquals(d.getNormalizedPrimaryNameOrOID(), "1.2.3");
  }



  /**
   * Check that the
   * {@link CommonSchemaElements#getNormalizedPrimaryNameOrOID()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNormalizedPrimaryNameOrOID() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertEquals(d.getNormalizedPrimaryNameOrOID(), "testtype");
  }



  /**
   * Check that the {@link CommonSchemaElements#getOID()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetOID() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertEquals(d.getOID(), "1.2.3");
  }



  /**
   * Check that the {@link CommonSchemaElements#getPrimaryName()}
   * method returns <code>null</code> when there is no primary name.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetPrimaryNameDefault() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder(null, "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertNull(d.getPrimaryName());
  }



  /**
   * Check that the {@link CommonSchemaElements#getPrimaryName()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetPrimaryName() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    CommonSchemaElements d = builder.getInstance();
    Assert.assertEquals(d.getPrimaryName(), "testType");
  }



  /**
   * Check that the {@link CommonSchemaElements#getSchemaFile()}
   * method returns <code>null</code> when there is no schema file.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetSchemaFileDefault() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder(null, "1.2.3");
    Assert.assertNull(getSchemaFile(builder.getInstance()));
  }



  /**
   * Check that the {@link CommonSchemaElements#getSchemaFile()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetSchemaFile() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder(null, "1.2.3");
    builder.addExtraProperty(
        ServerConstants.SCHEMA_PROPERTY_FILENAME, "/foo/bar");
    Assert.assertEquals(getSchemaFile(builder.getInstance()), "/foo/bar");
  }



  /**
   * Check that the {@link CommonSchemaElements#hasNameOrOID(String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testHasNameOrOID() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    CommonSchemaElements d = builder.getInstance();

    Assert.assertTrue(d.hasNameOrOID("testtype"));
    Assert.assertTrue(d.hasNameOrOID("1.2.3"));
    Assert.assertFalse(d.hasNameOrOID("x.y.z"));
  }



  /**
   * Check that the {@link CommonSchemaElements#isObsolete()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testIsObsolete() throws Exception {
    SchemaDefinitionBuilder builder = getBuilder("testType", "1.2.3");
    CommonSchemaElements d = builder.getInstance();

    Assert.assertFalse(d.isObsolete());

    builder = getBuilder("testType", "1.2.3");
    builder.setObsolete(true);
    d = builder.getInstance();

    Assert.assertTrue(d.isObsolete());
  }
}
