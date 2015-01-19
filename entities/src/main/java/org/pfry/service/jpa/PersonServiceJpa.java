package org.pfry.service.jpa;

import javax.persistence.EntityManager;

import org.pfry.entities.Person;
import org.pfry.service.PersonService;

public class PersonServiceJpa implements PersonService {
	
	EntityManager em;
	
	@Override 
	public void addPerson(int id, String name)
	{
		Person person = new Person();
		person.setId(id);
		person.setName(name);
		em.persist(person);
	}
	
	@Override
	public Person getPerson(int id)
	{
		return em.find(Person.class, id);
	}

	public EntityManager getEm() {
		return em;
	}

	public void setEm(EntityManager em) {
		this.em = em;
	}

}
