/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.remoting.rmi;

import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

/**
 * RMI exporter that exposes the specified service as RMI object with the specified name.
 * Such services can be accessed via plain RMI or via {@link RmiProxyFactoryBean}.
 * Also supports exposing any non-RMI service via RMI invokers, to be accessed via
 * {@link RmiClientInterceptor} / {@link RmiProxyFactoryBean}'s automatic detection
 * of such invokers.
 *
 * <p>With an RMI invoker, RMI communication works on the {@link RmiInvocationHandler}
 * level, needing only one stub for any service. Service interfaces do not have to
 * extend {@code java.rmi.Remote} or throw {@code java.rmi.RemoteException}
 * on all methods, but in and out parameters have to be serializable.
 *
 * <p>The major advantage of RMI, compared to Hessian, is serialization.
 * Effectively, any serializable Java object can be transported without hassle.
 * Hessian has its own (de-)serialization mechanisms, but is HTTP-based and thus
 * much easier to setup than RMI. Alternatively, consider Spring's HTTP invoker
 * to combine Java serialization with HTTP-based transport.
 *
 * <p>Note: RMI makes a best-effort attempt to obtain the fully qualified host name.
 * If one cannot be determined, it will fall back and use the IP address. Depending
 * on your network configuration, in some cases it will resolve the IP to the loopback
 * address. To ensure that RMI will use the host name bound to the correct network
 * interface, you should pass the {@code java.rmi.server.hostname} property to the
 * JVM that will export the registry and/or the service using the "-D" JVM argument.
 * For example: {@code -Djava.rmi.server.hostname=myserver.com}
 *
 * @author Juergen Hoeller
 * @since 13.05.2003
 * @see RmiClientInterceptor
 * @see RmiProxyFactoryBean
 * @see java.rmi.Remote
 * @see java.rmi.RemoteException
 * @see org.springframework.remoting.caucho.HessianServiceExporter
 * @see org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter
 */
public class RmiServiceExporter extends RmiBasedExporter implements InitializingBean, DisposableBean {

	private String serviceName;

	private int servicePort = 0;  // anonymous port

	private RMIClientSocketFactory clientSocketFactory;

	private RMIServerSocketFactory serverSocketFactory;

	private Registry registry;

	private String registryHost;

	private int registryPort = Registry.REGISTRY_PORT;

	private RMIClientSocketFactory registryClientSocketFactory;

	private RMIServerSocketFactory registryServerSocketFactory;

	private boolean alwaysCreateRegistry = false;

	private boolean replaceExistingBinding = true;

	private Remote exportedObject;

	private boolean createdRegistry = false;


	/**
	 * Set the name of the exported RMI service,
	 * i.e. {@code rmi://host:port/NAME}
	 */
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * Set the port that the exported RMI service will use.
	 * <p>Default is 0 (anonymous port).
	 */
	public void setServicePort(int servicePort) {
		this.servicePort = servicePort;
	}

	/**
	 * Set a custom RMI client socket factory to use for exporting the service.
	 * <p>If the given object also implements {@code java.rmi.server.RMIServerSocketFactory},
	 * it will automatically be registered as server socket factory too.
	 * @see #setServerSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see UnicastRemoteObject#exportObject(Remote, int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setClientSocketFactory(RMIClientSocketFactory clientSocketFactory) {
		this.clientSocketFactory = clientSocketFactory;
	}

	/**
	 * Set a custom RMI server socket factory to use for exporting the service.
	 * <p>Only needs to be specified when the client socket factory does not
	 * implement {@code java.rmi.server.RMIServerSocketFactory} already.
	 * @see #setClientSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see UnicastRemoteObject#exportObject(Remote, int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setServerSocketFactory(RMIServerSocketFactory serverSocketFactory) {
		this.serverSocketFactory = serverSocketFactory;
	}

	/**
	 * Specify the RMI registry to register the exported service with.
	 * Typically used in combination with RmiRegistryFactoryBean.
	 * <p>Alternatively, you can specify all registry properties locally.
	 * This exporter will then try to locate the specified registry,
	 * automatically creating a new local one if appropriate.
	 * <p>Default is a local registry at the default port (1099),
	 * created on the fly if necessary.
	 * @see RmiRegistryFactoryBean
	 * @see #setRegistryHost
	 * @see #setRegistryPort
	 * @see #setRegistryClientSocketFactory
	 * @see #setRegistryServerSocketFactory
	 */
	public void setRegistry(Registry registry) {
		this.registry = registry;
	}

	/**
	 * Set the host of the registry for the exported RMI service,
	 * i.e. {@code rmi://HOST:port/name}
	 * <p>Default is localhost.
	 */
	public void setRegistryHost(String registryHost) {
		this.registryHost = registryHost;
	}

	/**
	 * Set the port of the registry for the exported RMI service,
	 * i.e. {@code rmi://host:PORT/name}
	 * <p>Default is {@code Registry.REGISTRY_PORT} (1099).
	 * @see java.rmi.registry.Registry#REGISTRY_PORT
	 */
	public void setRegistryPort(int registryPort) {
		this.registryPort = registryPort;
	}

	/**
	 * Set a custom RMI client socket factory to use for the RMI registry.
	 * <p>If the given object also implements {@code java.rmi.server.RMIServerSocketFactory},
	 * it will automatically be registered as server socket factory too.
	 * @see #setRegistryServerSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see LocateRegistry#getRegistry(String, int, RMIClientSocketFactory)
	 */
	public void setRegistryClientSocketFactory(RMIClientSocketFactory registryClientSocketFactory) {
		this.registryClientSocketFactory = registryClientSocketFactory;
	}

	/**
	 * Set a custom RMI server socket factory to use for the RMI registry.
	 * <p>Only needs to be specified when the client socket factory does not
	 * implement {@code java.rmi.server.RMIServerSocketFactory} already.
	 * @see #setRegistryClientSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see LocateRegistry#createRegistry(int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setRegistryServerSocketFactory(RMIServerSocketFactory registryServerSocketFactory) {
		this.registryServerSocketFactory = registryServerSocketFactory;
	}

	/**
	 * Set whether to always create the registry in-process,
	 * not attempting to locate an existing registry at the specified port.
	 * <p>Default is "false". Switch this flag to "true" in order to avoid
	 * the overhead of locating an existing registry when you always
	 * intend to create a new registry in any case.
	 */
	public void setAlwaysCreateRegistry(boolean alwaysCreateRegistry) {
		this.alwaysCreateRegistry = alwaysCreateRegistry;
	}

	/**
	 * Set whether to replace an existing binding in the RMI registry,
	 * that is, whether to simply override an existing binding with the
	 * specified service in case of a naming conflict in the registry.
	 * <p>Default is "true", assuming that an existing binding for this
	 * exporter's service name is an accidental leftover from a previous
	 * execution. Switch this to "false" to make the exporter fail in such
	 * a scenario, indicating that there was already an RMI object bound.
	 */
	public void setReplaceExistingBinding(boolean replaceExistingBinding) {
		this.replaceExistingBinding = replaceExistingBinding;
	}


	/****
	 * java远程方法调用，即javaRMI （Java Remote Method Invocation），是Java编程语言里一种用于实现远程调用的应用程序编程接口，
	 * 它使用客户机上的运行的程序可以调用远程服务器上的对象，远程方法的调用特性使得java编程开发人员能够在网络分布式环境中操作，RMI
	 * 的全部宗旨就是尽可能地
	 *
	 */
	@Override
	public void afterPropertiesSet() throws RemoteException {
		prepare();
	}

	/**
	 * Initialize this service exporter, registering the service as RMI object.
	 * <p>Creates an RMI registry on the specified port if none exists.
	 * @throws RemoteException if service registration failed
	 * 首先我们从服务端的发布功能开始着手，同样，Spring中的核心还是配置文件，这是所有功能的基础，在服务端的配置文件中我们可以看到，定义
	 * 两个bean，其中一个对应接口的实现发布，而另一个则是对RMI服务的发布，使用org.springframework.remoting.RMI.RMIServiceExporter
	 * 类进行封装，其中包括服务类，服务名，服务接口，服务端口等若干属性，因此我们可以断定，org.springframework.remoting.RMI.RMIServiceExporter
	 * 类应该是发布RMI的关键类，我们可以从此类入手分析
	 * 	根据前面的示例，启动Spring的RMI服务并没有多余的操作，仅仅是开启Spring环境，new ClassPathXmlApplicationContext("test/remote/RMIServer.xml")
	 * 	仅此一句，于是我们分析很可能是RMIServiceException的初始化方法的时候做了些操作完成了端口的发布功能，那么这些操作入口是这个类的哪个方法里面
	 * 	呢，
	 * 	进入这个类，首先分析这个类的层次结构，如图12-1所示
	 * 	根据Eclipse的提供的功能，我们查看到了RMIServiceExporter的层次结构图，那么我们从这个层次图我们得到了什么信息呢，
	 *
	 * 	RMIServiceExporter实现了Spring中几个比较敏感的接口，BeanClassLoaderAware，DisposableBean,InitializingBean，其中
	 * 	DisposableBean接口的保证在实现该接口的bean销毁时调用destory方法，BeanClassLoaderAware接口保证在实现该接口bean初始化调用的
	 * 	时候setBeanClassLoader方法，而InitizingBean接口则是保证在实现该接口的bean初始化调用其afterPropertiesSet方法，所以我们推断
	 * 	RMIServiceExporter的初始化函数入口一定在其afterPropertiesSet或者setBeanClassLoader方法中，经过查看代码，确认afterPropertiesSet为
	 * 	RMIServiceExporter功能的初始化入口
	 *
	 *
	 *  果然 ，在afterPropertiesSet函数中将初出委托给了prepare，而在prepare方法中，我们找到了RMI服务发布的功能实现，同时，我们也大致
	 *  清楚了RMI服务发布的流程
	 *
	 *  验证 service
	 *  此处的service对应的配置中类型为RMIServiceExporter的service属性，它是实现类，并不是接口，尽管后期会对RMIServiceExporter做
	 *  一系列的封装，但是无论是怎样封装，最终还是会将逻辑引向到RMIServiceExporter来处理，所以，在发布之前需要验证
	 *
	 *  2.处理用户自定义的SocketFactory属性
	 *  在RMIServiceExporter中提供的4个套接字工厂配置，分别是clientSocketFactory，serviceSocketFactory和registerClientSocketFactory,
	 *  registerServiceSocketFactory，那么这两配置又有什么区别或者说分别应用在什么样的不同场景呢？
	 *
	 *  registryClientSocketFactory与registryServerSocketFactory用于主机与RMI服务器之间的创建，也就是当使用LocateRegistry.createRegistry(registryPost,clientSocketFactory,serverSocketFactory)
	 *  方法创建Register实例时会在RMI主机使用serverSocketFactory创建的套接字等待连接，而服务端与RMI主机通信时会使用clientSocketFactory
	 *  创建套接字
	 *
	 *  clientSocketFactory,serverSocketFactory同样是创建套接字，但是使用的位置不同，clientSocketFactory，serverSocketFactory
	 *  用于导出远程对象，serverSocketFactory用于在服务端建立套接字等待客户端连接，而ClientSocketFactory用于调用端建立套接字发起连接
	 *
	 *  3.根据配置参数获取Registry
	 *  4.构造对外发布的实例
	 *  5.构建对外发布的实例，当外界通过注册的服务名调用响应的方法时，RMI服务会将请求引入类来处理
	 *  在发布的RMI服务流程中，有几个步骤可能是我们比较关心的
	 *
	 *
	 *
	 *
	 *
	 *
	 *
	 */
	public void prepare() throws RemoteException {
		// 检查验证service
		checkService();

		if (this.serviceName == null) {
			throw new IllegalArgumentException("Property 'serviceName' is required");
		}

		// Check socket factories for exported object.
		// 如果用户在配置文件中配置了clientSocketFactory同时实现了RMIServerSocketFactory的处理
		// 如果配置中的clientSocketFactor同时又实现了RMServerSocketFactory接口那么会忽略配置中的serviceSocketFactory而使用clientSocketFactory代替
		//
		if (this.clientSocketFactory instanceof RMIServerSocketFactory) {
			this.serverSocketFactory = (RMIServerSocketFactory) this.clientSocketFactory;
		}
		//clientSocketFactory 和serviceSocketFactory要么同时出现，要么同时不出现
		if ((this.clientSocketFactory != null && this.serverSocketFactory == null) ||
				(this.clientSocketFactory == null && this.serverSocketFactory != null)) {
			throw new IllegalArgumentException(
					"Both RMIClientSocketFactory and RMIServerSocketFactory or none required");
		}

		// Check socket factories for RMI registry.
		/***
		 * 如果配置中的registryClientSocketFactory同时实现了RMIServiceSocketFactory接口，那么会忽略配置中的registerServiceSocketFactory
		 * 而使用registryClientSocketFactory代替
		 */
		if (this.registryClientSocketFactory instanceof RMIServerSocketFactory) {
			this.registryServerSocketFactory = (RMIServerSocketFactory) this.registryClientSocketFactory;
		}
		// 不允许出现只配置registerServerSocketFactory却没有配置registryClientSocketFactory的情况出现
		if (this.registryClientSocketFactory == null && this.registryServerSocketFactory != null) {
			throw new IllegalArgumentException(
					"RMIServerSocketFactory without RMIClientSocketFactory for registry not supported");
		}

		this.createdRegistry = false;

		// Determine RMI registry to use.
		// 确定RMI registry
		if (this.registry == null) {
			// 获取register
			// 对RMI稍有了解就会知道，由于底层的封装，获取Registry实例是非常简单的，只需要使用一个函数LocateRegistry.createRegisty(...)
			// 创建registry实例就可以了，但是Spring中并没有这么做，而是考虑了更多，比如RMI注册主机与发布的服务并不在同一台机器上，那么需要
			// 使用LocateRegister.getRegistry(registryHost,registryPort,clientSocketFactory)去远程获取Registry实例
			//
			this.registry = getRegistry(this.registryHost, this.registryPort,
				this.registryClientSocketFactory, this.registryServerSocketFactory);
			this.createdRegistry = true;
		}

		// Initialize and cache exported object.
		// 初始化以及缓存导出Object
		// 此时通常情况下是使用RMIInvocationWrapper封装的JDB代理类，切面为ReomteInvocationTraceInterceptor

		this.exportedObject = getObjectToExport();

		if (logger.isInfoEnabled()) {
			logger.info("Binding service '" + this.serviceName + "' to RMI registry: " + this.registry);
		}

		// Export RMI object.
		//
		if (this.clientSocketFactory != null) {
			//使用由给定的套接字工厂指定的传送方式导出远程对象，以便能够接收传入的调用
			// clientSocketFactory : 进行远程对象的调用是客户端套接字工厂
			// serverSocketFactory : 接收远程调用的服务端套接字工厂
			UnicastRemoteObject.exportObject(
					this.exportedObject, this.servicePort, this.clientSocketFactory, this.serverSocketFactory);
		}
		else {
			// 导出remote object ，以使它接收特定的端口的调用
			UnicastRemoteObject.exportObject(this.exportedObject, this.servicePort);
		}

		// Bind RMI object to registry.
		try {
			if (this.replaceExistingBinding) {
				this.registry.rebind(this.serviceName, this.exportedObject);
			}
			else {
				// 绑定服务名称到remote object ,外界调用的serviceName的时候会被exportedObject接收
				this.registry.bind(this.serviceName, this.exportedObject);
			}
		}
		catch (AlreadyBoundException ex) {
			// Already an RMI object bound for the specified service name...
			unexportObjectSilently();
			throw new IllegalStateException(
					"Already an RMI object bound for name '"  + this.serviceName + "': " + ex.toString());
		}
		catch (RemoteException ex) {
			// Registry binding failed: let's unexport the RMI object as well.
			unexportObjectSilently();
			throw ex;
		}
	}


	/**
	 * Locate or create the RMI registry for this exporter.
	 * @param registryHost the registry host to use (if this is specified,
	 * no implicit creation of a RMI registry will happen)
	 * @param registryPort the registry port to use
	 * @param clientSocketFactory the RMI client socket factory for the registry (if any)
	 * @param serverSocketFactory the RMI server socket factory for the registry (if any)
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created
	 *
	 * 如果并不是从另外的服务器上获取Register连接，那么需要在本地创建RMI的Register实例了，当然，这里有一个关键的参数alwaysCreateRegistry
	 * 如果此参数的配置为true,那么就在获取register实例的时候首先测试是否已经建立了对应的指定端口的连接，如果已经建立了则复用已经创建的实例
	 * 否则会重新创建
	 * 当然，之前也提到过，创建register实例时，可以使用自定义连接工厂，而之前的判断也保证了clientSocketFactory与serverSocketFactory
	 * 要么同时出现，所以这里只对clientSocketFactory是否为空进行判断
	 *
	 *
	 */
	protected Registry getRegistry(String registryHost, int registryPort,
			@Nullable RMIClientSocketFactory clientSocketFactory, @Nullable RMIServerSocketFactory serverSocketFactory)
			throws RemoteException {

		if (registryHost != null) {
			// Host explicitly specified: only lookup possible.
			// 远程连接测试
			if (logger.isInfoEnabled()) {
				logger.info("Looking for RMI registry at port '" + registryPort + "' of host [" + registryHost + "]");
			}
			// 如果registerHost不为空，则尝试获取对应的主机Register
			// 使用 clientSocketFactory 创建Registry
			Registry reg = LocateRegistry.getRegistry(registryHost, registryPort, clientSocketFactory);
			testRegistry(reg);
			return reg;
		}

		else {
			// 获取本机的Register
			return getRegistry(registryPort, clientSocketFactory, serverSocketFactory);
		}
	}

	/**
	 * Locate or create the RMI registry for this exporter.
	 * @param registryPort the registry port to use
	 * @param clientSocketFactory the RMI client socket factory for the registry (if any)
	 * @param serverSocketFactory the RMI server socket factory for the registry (if any)
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created

	 *
	 */
	protected Registry getRegistry(int registryPort,
			@Nullable RMIClientSocketFactory clientSocketFactory, @Nullable RMIServerSocketFactory serverSocketFactory)
			throws RemoteException {

		if (clientSocketFactory != null) {
			if (this.alwaysCreateRegistry) {
				logger.info("Creating new RMI registry");
				// 使用clientSocketFactory 创建Registry
				return LocateRegistry.createRegistry(registryPort, clientSocketFactory, serverSocketFactory);
			}
			if (logger.isInfoEnabled()) {
				logger.info("Looking for RMI registry at port '" + registryPort + "', using custom socket factory");
			}
			synchronized (LocateRegistry.class) {
				try {
					// Retrieve existing registry.
					// 复用测试
					Registry reg = LocateRegistry.getRegistry(null, registryPort, clientSocketFactory);
					testRegistry(reg);
					return reg;
				}
				catch (RemoteException ex) {
					logger.debug("RMI registry access threw exception", ex);
					logger.info("Could not detect RMI registry - creating new one");
					// Assume no registry found -> create new one.
					return LocateRegistry.createRegistry(registryPort, clientSocketFactory, serverSocketFactory);
				}
			}
		}

		else {
			return getRegistry(registryPort);
		}
	}

	/**
	 * Locate or create the RMI registry for this exporter.
	 * @param registryPort the registry port to use
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created
	 * 如果创建Registry 实例时不需要使用自定义套接字，那么直接使用LocateRegistry.createRegistry(...)方法来创建了，当然，复用
	 * 的检测还是必要的
	 */
	protected Registry getRegistry(int registryPort) throws RemoteException {
		if (this.alwaysCreateRegistry) {
			logger.info("Creating new RMI registry");
			return LocateRegistry.createRegistry(registryPort);
		}
		if (logger.isInfoEnabled()) {
			logger.info("Looking for RMI registry at port '" + registryPort + "'");
		}
		synchronized (LocateRegistry.class) {
			try {
				// Retrieve existing registry.
				//查看对应的当前registerPort和Register是否已经创建，如果创建直接使用
				Registry reg = LocateRegistry.getRegistry(registryPort);
				// 测试是否可以用，如果不可以用，则抛出异常
				testRegistry(reg);
				return reg;
			}
			catch (RemoteException ex) {
				logger.debug("RMI registry access threw exception", ex);
				logger.info("Could not detect RMI registry - creating new one");
				// Assume no registry found -> create new one.
				// 根据端口创建Registry
				return LocateRegistry.createRegistry(registryPort);
			}
		}
	}

	/**
	 * Test the given RMI registry, calling some operation on it to
	 * check whether it is still active.
	 * <p>Default implementation calls {@code Registry.list()}.
	 * @param registry the RMI registry to test
	 * @throws RemoteException if thrown by registry methods
	 * @see java.rmi.registry.Registry#list()
	 */
	protected void testRegistry(Registry registry) throws RemoteException {
		registry.list();
	}


	/**
	 * Unbind the RMI service from the registry on bean factory shutdown.
	 */
	@Override
	public void destroy() throws RemoteException {
		if (logger.isInfoEnabled()) {
			logger.info("Unbinding RMI service '" + this.serviceName +
					"' from registry" + (this.createdRegistry ? (" at port '" + this.registryPort + "'") : ""));
		}
		try {
			this.registry.unbind(this.serviceName);
		}
		catch (NotBoundException ex) {
			if (logger.isWarnEnabled()) {
				logger.warn("RMI service '" + this.serviceName + "' is not bound to registry"
						+ (this.createdRegistry ? (" at port '" + this.registryPort + "' anymore") : ""), ex);
			}
		}
		finally {
			unexportObjectSilently();
		}
	}

	/**
	 * Unexport the registered RMI object, logging any exception that arises.
	 */
	private void unexportObjectSilently() {
		try {
			UnicastRemoteObject.unexportObject(this.exportedObject, true);
		}
		catch (NoSuchObjectException ex) {
			if (logger.isWarnEnabled()) {
				logger.warn("RMI object for service '" + this.serviceName + "' isn't exported anymore", ex);
			}
		}
	}
}
