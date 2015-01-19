package org.pfry.service;

import org.pfry.entities.Person;

public interface PersonService {

	void addPerson(int id, String name);
 
	Person getPerson(int id);

}
