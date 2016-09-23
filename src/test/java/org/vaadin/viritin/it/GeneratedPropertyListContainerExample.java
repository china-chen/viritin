package org.vaadin.viritin.it;

import com.vaadin.annotations.Theme;
import com.vaadin.data.Item;
import com.vaadin.data.util.PropertyValueGenerator;
import com.vaadin.ui.Component;
import org.vaadin.addonhelpers.AbstractTest;
import org.vaadin.viritin.grid.GeneratedPropertyListContainer;
import org.vaadin.viritin.grid.MGrid;
import org.vaadin.viritin.layouts.MVerticalLayout;
import org.vaadin.viritin.testdomain.Address;
import org.vaadin.viritin.testdomain.Person;

import java.util.List;
import org.vaadin.viritin.testdomain.Service;

/**
 *
 * @author Matti Tahvonen
 */
@Theme("valo")
public class GeneratedPropertyListContainerExample extends AbstractTest {

    private final MGrid<Person> fashionableApiGrid = new MGrid<>(Person.class)
            .setRows(Service.getListOfPersons(100))
            .withGeneratedColumn("fullname", p -> p.getFirstName() + " " + p.getLastName())
            .withGeneratedColumn("groupnumber", Integer.class, p -> p.getGroups() != null ? p.getGroups().size() : 0)
            .withGeneratedColumn("details", new DetailsGenerator())
            .withProperties("id", "fullname", "groupnumber", "details")
            .withFullWidth();

    private final MGrid<Person> legacyApiGrid = new MGrid<>();

    @Override
    public Component getTestComponent() {

        GeneratedPropertyListContainer<Person> container = new
                GeneratedPropertyListContainer(Person.class,
                "id", "fullname", "groupnumber", "details");
        container.addGeneratedProperty("fullname", p -> p.getFirstName() + " " + p.getLastName());
        container.addGeneratedProperty("groupnumber", Integer.class, p -> p.getGroups() != null ? p.getGroups().size() : 0);
        container.addGeneratedProperty("details", new DetailsGenerator());
        container.addAll(Service.getListOfPersons(100));
        legacyApiGrid.setContainerDataSource(container);
        legacyApiGrid.getColumn("details").setHeaderCaption("Details");
        legacyApiGrid.setSizeFull();

        return new MVerticalLayout(fashionableApiGrid, legacyApiGrid);
    }

    public class DetailsGenerator extends PropertyValueGenerator<String> {

        @Override
        public String getValue(Item item, Object itemId, Object propertyId) {
            Person p = (Person)itemId;
            StringBuilder displayValue = new StringBuilder();
            List<Address> addresses = p.getAddresses();
            if (addresses != null && !addresses.isEmpty()) {
                displayValue.append("Addresses: ");
                for (Address address : addresses) {
                    if (address == null) {
                        continue;
                    }
                    displayValue.append(address.getZipCode());
                    displayValue.append("; ");
                    displayValue.append(address.getCity());
                    displayValue.append("; ");
                    displayValue.append(address.getStreet());
                }
            }
            return displayValue.toString();
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }
    }


}
